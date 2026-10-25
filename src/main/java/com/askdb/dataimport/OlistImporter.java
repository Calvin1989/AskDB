package com.askdb.dataimport;

import com.askdb.config.AskDbProperties;
import com.askdb.schema.SchemaReader;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 把 Olist 数据集的 CSV 导入 MySQL：先执行 db/olist-schema.sql 重建表，再逐个导入 CSV。
 * 只在 import 配置下启动：mvn spring-boot:run -Dspring-boot.run.profiles=import
 * <p>
 * CSV 列和表字段按位置对应，空字符串存成 NULL。
 * 地理位置表（olist_geolocation_dataset.csv，约 100 万行）查询用不到，没有导入。
 */
@Component
@Profile("import")
public class OlistImporter implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(OlistImporter.class);

    /** 表名 → CSV 文件名。表之间没有建外键，导入顺序无所谓。 */
    private static final Map<String, String> TABLE_FILES = Map.ofEntries(
            Map.entry("customers", "olist_customers_dataset.csv"),
            Map.entry("sellers", "olist_sellers_dataset.csv"),
            Map.entry("products", "olist_products_dataset.csv"),
            Map.entry("category_translation", "product_category_name_translation.csv"),
            Map.entry("orders", "olist_orders_dataset.csv"),
            Map.entry("order_items", "olist_order_items_dataset.csv"),
            Map.entry("order_payments", "olist_order_payments_dataset.csv"),
            Map.entry("order_reviews", "olist_order_reviews_dataset.csv"));

    private static final CSVFormat CSV = CSVFormat.DEFAULT.builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .build();

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;
    private final SchemaReader schemaReader;
    private final AskDbProperties.DataImport config;

    public OlistImporter(DataSource dataSource, SchemaReader schemaReader, AskDbProperties properties) {
        this.dataSource = dataSource;
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.schemaReader = schemaReader;
        this.config = properties.dataImport();
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        Path dataDir = resolveDataDir();

        log.info("重建表结构");
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(new ClassPathResource("db/olist-schema.sql"));
        populator.setSqlScriptEncoding(StandardCharsets.UTF_8.name()); // Windows 默认编码是 GBK，必须指定
        populator.execute(dataSource);

        for (var entry : TABLE_FILES.entrySet()) {
            importTable(entry.getKey(), dataDir.resolve(entry.getValue()));
        }
        schemaReader.refresh();
        log.info("导入完成");
    }

    private Path resolveDataDir() {
        if (config.dataDir() == null || config.dataDir().isBlank()) {
            throw new IllegalStateException("请配置 askdb.data-import.data-dir（Olist CSV 所在目录）");
        }
        Path dir = Path.of(config.dataDir());
        for (String file : TABLE_FILES.values()) {
            if (!Files.isRegularFile(dir.resolve(file))) {
                throw new IllegalStateException("找不到文件：" + dir.resolve(file).toAbsolutePath());
            }
        }
        return dir;
    }

    private void importTable(String table, Path csvFile) throws IOException {
        long start = System.currentTimeMillis();
        int count = 0;
        try (Reader reader = Files.newBufferedReader(csvFile, StandardCharsets.UTF_8);
             CSVParser parser = CSV.parse(reader)) {

            int columnCount = parser.getHeaderNames().size();
            String sql = "INSERT INTO " + table + " VALUES (" + String.join(",", Collections.nCopies(columnCount, "?")) + ")";

            List<Object[]> batch = new ArrayList<>(config.batchSize());
            for (CSVRecord record : parser) {
                batch.add(toRow(record));
                if (batch.size() == config.batchSize()) {
                    count += flush(sql, batch);
                }
            }
            count += flush(sql, batch);
        }
        log.info("{}：{} 行，用时 {} ms", table, count, System.currentTimeMillis() - start);
    }

    private static Object[] toRow(CSVRecord record) {
        Object[] row = new Object[record.size()];
        for (int i = 0; i < row.length; i++) {
            String value = record.get(i);
            row[i] = value.isEmpty() ? null : value;
        }
        return row;
    }

    private int flush(String sql, List<Object[]> batch) {
        if (batch.isEmpty()) {
            return 0;
        }
        jdbcTemplate.batchUpdate(sql, batch);
        int size = batch.size();
        batch.clear();
        return size;
    }
}
