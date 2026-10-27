package com.yunwms.app;

import com.yunwms.master.application.command.ChangeOwnerStatusCommand;
import com.yunwms.master.application.service.OwnerStatusChangeService;
import com.yunwms.master.domain.port.CurrentOperator;
import com.yunwms.web.api.ErrorCode;
import com.yunwms.web.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

/**
 * 货主状态流转的并发回归：真实 PostgreSQL + 真实 Spring 事务代理 + 双线程。
 *
 * <p><b>要证的唯一一件事：</b>终止一旦返回成功，最终状态就必须是 {@code TERMINATED}。
 * 修复前 {@code change} 是「先读状态、再在无事务无锁的 UPDATE 里写状态」，
 * 并发的暂停 / 恢复可以在终止之后落库，把它覆盖回去——终止返回了成功，
 * 而货主已经不是终态。（复现数据见 {@code openspec/changes/fix-owner-status-concurrency/proposal.md}）
 *
 * <p><b>为什么必须是集成测试而不是 Mockito 单测：</b>本用例要验证的是「行锁 + 事务边界」这件事，
 * 而单测里只有 mock：mock 没有锁，也没有提交与回滚，任何实现都能通过。
 * 同理，测试方法本身<b>不加 {@code @Transactional}</b>——那样两次 {@code change} 会共享同一个连接与事务，
 * 锁永远不冲突，测出来的是「顺序执行」。
 *
 * <p><b>为什么操作人用 mock：</b>并发线程里没有 HTTP 请求，{@code SaTokenCurrentOperator} 取不到登录态。
 * 固定操作人只是把「谁在操作」这个与并发语义无关的变量换成常量，被验证的事务与锁仍走生产实现。
 *
 * <p><b>断言只钉结论，不钉执行顺序：</b>两个线程谁先拿到锁由数据库决定，且这是允许的两种合法执行顺序。
 * 因此断言的是「终止必须成功」「最终必须是 TERMINATED」「失败的那一方只允许是 40900」，
 * 而不是「谁先谁后」——后者会让用例在机器快慢不同时随机变红。
 */
@DisplayName("货主状态流转并发")
class OwnerStatusConcurrencyIntegrationTest extends AbstractIntegrationTest {

    /**
     * 操作人标识，写进 {@code updated_by}。
     *
     * <p>{@code owner.created_by} 没有外键，取一个不与种子账号重合的值即可；不复用 {@code 1}，
     * 是为了在库里看到这个值时能立刻认出「这是并发用例写的」。
     */
    private static final long OPERATOR_ID = 990_001L;

    /**
     * 每种并发组合重复的轮数。
     *
     * <p>单个竞态窗口只有毫秒级，一轮有概率碰巧串行而看不出问题（修复前的实测是 20 轮里 6~12 轮暴露）。
     * 多跑几轮的代价很低（每轮两条事务），换来的是修复被回退时这条用例能稳定变红。
     */
    private static final int ROUNDS = 5;

    /** 等待两个线程就位与等待单个请求完成的上限；超时说明线程或锁出了问题，按失败处理而不是无限等待。 */
    private static final long TIMEOUT_SECONDS = 30L;

    /**
     * 用例自造货主的序号，用于生成互不重复的编码与前缀。
     *
     * <p>整个测试 JVM 共用一个库且不做数据清理，因此每个货主必须自带唯一的业务键；
     * 序号是静态的，同一类的多个用例之间也不会撞。
     */
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    /**
     * SKU 前缀的字符集：{@code A-H} + {@code J-N} + {@code P-Z} + {@code 2-9}，
     * 与 {@code SkuPrefix} 的规则一致（排除易混淆的 {@code 0}、{@code O}、{@code 1}、{@code I}）。
     * 生成的前缀必须以这里面的字符开头，否则从库里重建聚合时会当场失败。
     */
    private static final char[] PREFIX_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

    @Autowired
    private OwnerStatusChangeService ownerStatusChangeService;

    /**
     * 固定操作人的测试替身：并发线程没有 HTTP 会话，真实实现读不到登录态。
     *
     * <p>只替换「当前是谁」这一个技术性事实，仓储、事务管理、MyBatis 与数据库全部是生产实现，
     * 因此本用例仍然在验证真实的行为。
     */
    @MockitoBean
    private CurrentOperator currentOperator;

    @BeforeEach
    void fixCurrentOperator() {
        // 每个用例方法前重新固定：MockitoBean 在每个用例结束后会被重置
        given(currentOperator.currentUserId()).willReturn(OptionalLong.of(OPERATOR_ID));
    }

    /** 修复前的表现：终止与暂停同时提交时，最终状态约有半数概率落回 {@code SUSPENDED}。 */
    @Test
    @DisplayName("ACTIVE 同时终止与暂停：终止成功后最终必须为 TERMINATED")
    void shouldKeepTerminatedWhenTerminationRacesSuspension() throws Exception {
        for (int round = 1; round <= ROUNDS; round++) {
            long ownerId = seedOwner("ACTIVE");

            List<Outcome> outcomes = race(ownerId, "TERMINATED", "SUSPENDED");
            Outcome termination = outcomeOf(outcomes, "TERMINATED");
            Outcome suspension = outcomeOf(outcomes, "SUSPENDED");

            assertThat(termination.succeeded())
                    .as("第 %d 轮：ACTIVE 与 SUSPENDED 都允许流转到终止，终止必须成功；实际：%s", round, outcomes)
                    .isTrue();
            assertThat(statusOf(ownerId))
                    .as("第 %d 轮：终止已返回成功，最终状态不得被并发的暂停覆盖；实际：%s", round, outcomes)
                    .isEqualTo("TERMINATED");
            assertRejectionOnlyMeansTerminalState(suspension, round, outcomes);
        }
    }

    /** 修复前的表现：终止与恢复同时提交时，最终状态约有半数概率落回 {@code ACTIVE}。 */
    @Test
    @DisplayName("SUSPENDED 同时终止与恢复：终止成功后最终必须为 TERMINATED")
    void shouldKeepTerminatedWhenTerminationRacesResume() throws Exception {
        for (int round = 1; round <= ROUNDS; round++) {
            long ownerId = seedOwner("SUSPENDED");

            List<Outcome> outcomes = race(ownerId, "TERMINATED", "ACTIVE");
            Outcome termination = outcomeOf(outcomes, "TERMINATED");
            Outcome resume = outcomeOf(outcomes, "ACTIVE");

            assertThat(termination.succeeded())
                    .as("第 %d 轮：SUSPENDED 与 ACTIVE 都允许流转到终止，终止必须成功；实际：%s", round, outcomes)
                    .isTrue();
            assertThat(statusOf(ownerId))
                    .as("第 %d 轮：终止已返回成功，最终状态不得被并发的恢复覆盖；实际：%s", round, outcomes)
                    .isEqualTo("TERMINATED");
            assertRejectionOnlyMeansTerminalState(resume, round, outcomes);
        }
    }

    /**
     * 重复终止：两次都成功且最终为 {@code TERMINATED}。
     *
     * <p>同状态提交是幂等成功（{@code OwnerStatus#canTransitionTo} 对自身返回 {@code true}），
     * 因此这里两次调用都不得报错——加锁把并发变成串行之后，「后到者读到已终止」这条路径
     * 从偶发变成必然，幂等口径必须仍然成立。
     */
    @Test
    @DisplayName("ACTIVE 重复终止：两次都成功，最终为 TERMINATED")
    void shouldTreatConcurrentRepeatedTerminationAsIdempotent() throws Exception {
        for (int round = 1; round <= ROUNDS; round++) {
            long ownerId = seedOwner("ACTIVE");

            List<Outcome> outcomes = race(ownerId, "TERMINATED", "TERMINATED");

            assertThat(outcomes).as("第 %d 轮：两个线程都必须跑完", round).hasSize(2);
            for (Outcome outcome : outcomes) {
                assertThat(outcome.succeeded())
                        .as("第 %d 轮：重复终止是幂等成功；实际：%s", round, outcomes)
                        .isTrue();
            }
            assertThat(statusOf(ownerId))
                    .as("第 %d 轮：重复终止后必须是终态；实际：%s", round, outcomes)
                    .isEqualTo("TERMINATED");
        }
    }

    /**
     * 让两个目标状态在同一时刻对同一个货主发起流转。
     *
     * <p>用两个栅栏而不是 {@code Thread#start} 直接跑：{@code start} 只是让线程进入可运行状态，
     * 真正执行到 {@code change} 可能相差几十毫秒，那样两个事务根本不会重叠，测出来的是串行执行。
     * 两个线程都走到 {@code ready} 之后由主线程放行，才是有意制造的重叠。
     *
     * @param ownerId      被流转的货主标识
     * @param targetStatus 第一个线程的目标状态
     * @param otherTarget  第二个线程的目标状态
     * @return 两个线程各自的流转结果，顺序与入参一致
     * @throws Exception 线程执行过程中抛出非业务异常，或等待超时
     */
    private List<Outcome> race(long ownerId, String targetStatus, String otherTarget) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Outcome> first = pool.submit(() -> changeAfterBarrier(ready, start, ownerId, targetStatus));
            Future<Outcome> second = pool.submit(() -> changeAfterBarrier(ready, start, ownerId, otherTarget));

            assertThat(ready.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
                    .as("两个线程必须都就位后再起跑，否则测到的不是并发")
                    .isTrue();
            start.countDown();

            return List.of(first.get(TIMEOUT_SECONDS, TimeUnit.SECONDS),
                    second.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
    }

    /**
     * 在栅栏后调用一次状态流转，并把业务拒绝收成结果对象。
     *
     * <p>只捕获 {@link BizException}：它是「用例正常拒绝」的唯一形式。其他异常（连接失败、
     * 事务异常）不被收成结果，会顺着 {@code Future#get} 冒到用例上——把基础设施故障当成
     * 「这次流转被拒绝」会让一个坏掉的用例看起来像通过。
     *
     * @param ready        就位栅栏，进入方法即计数减一
     * @param start        起跑栅栏，等待主线程放行
     * @param ownerId      货主标识
     * @param targetStatus 目标状态编码
     * @return 本次流转的结果
     * @throws Exception 栅栏等待超时或出现非业务异常
     */
    private Outcome changeAfterBarrier(CountDownLatch ready, CountDownLatch start, long ownerId, String targetStatus)
            throws Exception {
        ready.countDown();
        if (!start.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            throw new IllegalStateException("并发用例的起跑栅栏超时，两个线程未能同时就位");
        }
        try {
            // acknowledgedImpact 恒为 true：本用例验证的是并发，不是终止前的确认流程；
            // 让影响清单这一步不参与，锁内只有锁读、判定与更新，竞态窗口才与生产一致
            ownerStatusChangeService.change(new ChangeOwnerStatusCommand(ownerId, targetStatus, true));
            return new Outcome(targetStatus, true, null);
        } catch (BizException e) {
            return new Outcome(targetStatus, false, e.errorCode());
        }
    }

    /**
     * 断言「被拒绝的那一方只可能是被终止态挡住」。
     *
     * <p>并发下暂停 / 恢复有两种合法结局：先拿到锁则成功，后拿到锁则读到 {@code TERMINATED}
     * 并被终态拒绝。因此这里只在它失败时校验错误码，不要求它必须失败——
     * 强制要求「一定失败」等于假设某个线程必然先跑，那是执行顺序，不是规格。
     *
     * @param outcome  可能被拒绝的那次流转
     * @param round    轮次，用于失败信息定位
     * @param outcomes 本轮两个线程的完整结果
     */
    private static void assertRejectionOnlyMeansTerminalState(Outcome outcome, int round, List<Outcome> outcomes) {
        if (outcome.succeeded()) {
            return;
        }
        assertThat(outcome.errorCode())
                .as("第 %d 轮：%s 失败只可能是「终止为终态」的 %s，不能是别的错误；实际：%s",
                        round, outcome.targetStatus(), ErrorCode.CONFLICT, outcomes)
                .isEqualTo(ErrorCode.CONFLICT);
    }

    /**
     * 按目标状态取出对应线程的结果。
     *
     * @param outcomes      两个线程的结果
     * @param targetStatus  目标状态编码
     * @return 该目标状态对应的结果
     */
    private static Outcome outcomeOf(List<Outcome> outcomes, String targetStatus) {
        return outcomes.stream()
                .filter(outcome -> outcome.targetStatus().equals(targetStatus))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("没有找到目标状态为 " + targetStatus + " 的流转结果"));
    }

    /**
     * 用 JDBC 直接造一条指定状态的货主。
     *
     * <p>不走建档接口：本用例要固定初始状态与业务键，且不应被字典校验、取号引擎的失败牵连——
     * 那些链路已有自己的用例。{@code owner} 的必填列按 {@code V001} 给全，审计列交给库默认值。
     *
     * @param status 初始状态编码，取 {@code OwnerStatus} 的枚举名
     * @return 新货主的标识
     */
    private long seedOwner(String status) {
        int index = SEQUENCE.incrementAndGet();
        String code = String.format("CONC-%04d", index);
        jdbcTemplate.update("""
                INSERT INTO owner (code, name, sku_prefix, owner_type, settlement_entity, country, city, address_line, status)
                VALUES (?, ?, ?, 'BRAND', '并发测试结算主体', 'CN', '深圳', '科技园路 9 号', ?)
                """, code, "并发病例货主" + index, skuPrefix(index), status);
        return jdbcTemplate.queryForObject("SELECT id FROM owner WHERE code = ?", Long.class, code);
    }

    /**
     * 由序号生成一个合法的 SKU 前缀。
     *
     * <p>固定首字符 {@code C}：{@code ux_owner_sku_prefix} 是全表唯一索引，其余测试类用的是
     * {@code LCA*}、{@code TGX*} 等前缀，固定一个别人不用的首字母就不会与它们撞。
     * 后三位按 32 进制取字符集，因此同一进程内序号不同则前缀必然不同。
     *
     * @param index 从 1 开始的序号
     * @return 形如 {@code C2KM} 的 4 位前缀
     */
    private static String skuPrefix(int index) {
        char[] prefix = new char[4];
        prefix[0] = 'C';
        int remaining = index;
        for (int position = 3; position >= 1; position--) {
            prefix[position] = PREFIX_ALPHABET[remaining % PREFIX_ALPHABET.length];
            remaining /= PREFIX_ALPHABET.length;
        }
        return new String(prefix);
    }

    /**
     * 直接查库取当前状态，绕开接口与仓储。
     *
     * <p>断言的是「库里最终是什么」，而不是某个返回值：修复前的问题恰恰是接口返回成功、
     * 库里却是别的状态，只看返回值会把 bug 读成正常。
     *
     * @param ownerId 货主标识
     * @return 库里的状态编码
     */
    private String statusOf(long ownerId) {
        return jdbcTemplate.queryForObject("SELECT status FROM owner WHERE id = ?", String.class, ownerId);
    }

    /**
     * 一次流转的结果。
     *
     * @param targetStatus 目标状态编码
     * @param succeeded    是否成功（未被业务规则拒绝）
     * @param errorCode    被拒绝时的错误码；成功时为 {@code null}
     */
    private record Outcome(String targetStatus, boolean succeeded, ErrorCode errorCode) {
    }
}
