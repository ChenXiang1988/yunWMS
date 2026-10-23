package com.yunwms.app;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 启动入口。
 *
 * <p>扫描范围覆盖 auth 与 master 两个业务模块，故包名收在 {@code com.yunwms}。
 * 本类只负责装配，不放任何业务逻辑。
 */
@SpringBootApplication(scanBasePackages = "com.yunwms")
@MapperScan("com.yunwms.**.mapper")
public class WmsApplication {

    public static void main(String[] args) {
        SpringApplication.run(WmsApplication.class, args);
    }
}
