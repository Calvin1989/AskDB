package com.askdb.dataimport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;

/**
 * 建只读账号和日志、反馈表：用 root 执行 db/setup-reader.sql，再把 askdb_reader 的密码设成 DB_PASSWORD。
 * 只在 setup-reader 配置下启动：mvn spring-boot:run -Dspring-boot.run.profiles=setup-reader
 * <p>
 * 这样密码只写在 askdb-local.yml 一个地方，不用再手动执行 ALTER USER。可以重复执行。
 */
@Component
@Profile("setup-reader")
public class ReaderAccountSetup implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ReaderAccountSetup.class);
    private static final String READER = "askdb_reader";

    private final DataSource dataSource;
    private final String readerPassword;
    private final String serviceUsername;

    public ReaderAccountSetup(DataSource dataSource,
                              @Value("${DB_PASSWORD:}") String readerPassword,
                              @Value("${DB_USERNAME:" + READER + "}") String serviceUsername) {
        this.dataSource = dataSource;
        this.readerPassword = readerPassword;
        this.serviceUsername = serviceUsername;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (readerPassword.isBlank() || readerPassword.startsWith("改成")) {
            throw new IllegalStateException("先在 askdb-local.yml 里把 DB_PASSWORD 设成只读账号要用的密码，再运行");
        }

        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(new ClassPathResource("db/setup-reader.sql"));
        populator.setSqlScriptEncoding(StandardCharsets.UTF_8.name()); // Windows 默认编码是 GBK，必须指定
        populator.execute(dataSource);

        // 密码作为参数传入，由驱动转义，密码里有引号等符号也没问题
        new JdbcTemplate(dataSource).update("ALTER USER '" + READER + "'@'%' IDENTIFIED BY ?", readerPassword);
        log.info("只读账号 {} 已就绪，日志表和反馈表已创建", READER);

        if (!READER.equals(serviceUsername)) {
            log.warn("askdb-local.yml 里的 DB_USERNAME 是 {}，服务不会用只读账号。删掉这一行就会用 {}", serviceUsername, READER);
        }
    }
}
