package com.askdb.glossary;

import com.askdb.config.AskDbProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;

/**
 * 业务词典：问题里提到"GMV""复购"这类词时，把对应的口径定义交给模型。
 * <p>
 * 模型不知道这份数据里 GMV 要不要算运费、客户数该按哪个字段去重，不告诉它就只能猜。
 * 定义来自 docs/business-terms.md，机器读取的版本在 resources/glossary.json，两边要保持一致。
 */
@Service
public class GlossaryService {

    private static final Logger log = LoggerFactory.getLogger(GlossaryService.class);

    private final List<Term> terms;
    private final boolean enabled;

    public GlossaryService(ObjectMapper objectMapper, AskDbProperties properties) {
        this.enabled = properties.glossary().enabled();
        try (InputStream in = properties.glossary().file().getInputStream()) {
            this.terms = List.copyOf(objectMapper.readValue(in, new TypeReference<List<Term>>() {
            }));
        } catch (IOException e) {
            throw new UncheckedIOException("读取业务词典失败：" + properties.glossary().file(), e);
        }
        log.info("业务词典：{} 个术语（{}）", terms.size(), enabled ? "已启用" : "未启用");
    }

    /** 问题里用到的术语；词典关闭时返回空 */
    public List<Term> match(String question) {
        return enabled ? match(terms, question) : List.of();
    }

    static List<Term> match(List<Term> terms, String question) {
        return terms.stream().filter(t -> t.matches(question)).toList();
    }
}
