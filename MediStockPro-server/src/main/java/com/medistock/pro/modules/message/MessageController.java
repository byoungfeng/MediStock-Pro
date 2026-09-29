package com.medistock.pro.modules.message;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import com.medistock.pro.common.PageResult;
import com.medistock.pro.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 消息中心 (P004)
 */
@Tag(name = "消息中心")
@RestController
@RequestMapping("/api/v1/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    @Operation(summary = "我的消息分页")
    @GetMapping
    @SaCheckPermission("MESSAGE_VIEW")
    public Result<PageResult<Message>> page(@RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "20") int size,
                                            @RequestParam(required = false) String type,
                                            @RequestParam(required = false) Boolean unreadOnly) {
        return Result.success(PageResult.of(
                messageService.pageByUser(StpUtil.getLoginIdAsLong(), type, unreadOnly, page, size)));
    }

    @Operation(summary = "未读数")
    @GetMapping("/unread-count")
    public Result<Long> unreadCount() {
        return Result.success(messageService.unreadCount(StpUtil.getLoginIdAsLong()));
    }

    @Operation(summary = "标记已读")
    @PostMapping("/{id}/read")
    @SaCheckPermission("MESSAGE_MANAGE")
    public Result<Void> markRead(@PathVariable Long id) {
        messageService.markRead(id, StpUtil.getLoginIdAsLong());
        return Result.success();
    }

    @Operation(summary = "全部已读")
    @PostMapping("/read-all")
    @SaCheckPermission("MESSAGE_MANAGE")
    public Result<Void> markAllRead() {
        messageService.markAllRead(StpUtil.getLoginIdAsLong());
        return Result.success();
    }
}
