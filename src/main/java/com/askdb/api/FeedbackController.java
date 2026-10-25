package com.askdb.api;

import com.askdb.examples.ExampleStore;
import com.askdb.examples.ExampleStore.Verdict;
import com.askdb.sql.UnsafeSqlException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 用户对结果的反馈。标记为"正确"的问题和 SQL 会进入示例库，以后遇到相似问题时交给模型参考。
 */
@RestController
@RequestMapping("/api/feedback")
public class FeedbackController {

    private final ExampleStore exampleStore;

    public FeedbackController(ExampleStore exampleStore) {
        this.exampleStore = exampleStore;
    }

    public record FeedbackRequest(
            @NotBlank @Size(max = 500) String question,
            @NotBlank @Size(max = 10_000) String sql,
            @NotNull Verdict verdict) {
    }

    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void submit(@Valid @RequestBody FeedbackRequest request) {
        try {
            exampleStore.record(request.question().trim(), request.sql(), request.verdict());
        } catch (UnsafeSqlException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "只能对查询语句反馈：" + e.getMessage());
        } catch (DataAccessException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "保存反馈失败。如果刚升级过，请用 root 再执行一次 db/setup-reader.sql（新增了反馈表）。");
        }
    }
}
