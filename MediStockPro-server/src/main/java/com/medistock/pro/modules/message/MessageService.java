package com.medistock.pro.modules.message;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.medistock.pro.modules.message.mapper.MessageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 消息中心 (P004): 站内通知, 只增不改 + 已读标记
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageMapper messageMapper;

    /** 发送站内消息 (失败不阻断业务) */
    public void notify(Long userId, String type, String title, String content, String bizType, Long bizId) {
        if (userId == null) {
            return;
        }
        try {
            Message msg = new Message();
            msg.setUserId(userId);
            msg.setType(type);
            msg.setTitle(title);
            msg.setContent(content);
            msg.setBizType(bizType);
            msg.setBizId(bizId);
            msg.setRead(0);
            messageMapper.insert(msg);
        } catch (Exception e) {
            log.warn("消息写入失败: {}", e.getMessage());
        }
    }

    public Page<Message> pageByUser(Long userId, String type, Boolean unreadOnly, int page, int size) {
        return messageMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<Message>()
                        .eq(Message::getUserId, userId)
                        .eq(StringUtils.hasText(type), Message::getType, type)
                        .eq(Boolean.TRUE.equals(unreadOnly), Message::getRead, 0)
                        .orderByDesc(Message::getId));
    }

    public long unreadCount(Long userId) {
        return messageMapper.selectCount(new LambdaQueryWrapper<Message>()
                .eq(Message::getUserId, userId)
                .eq(Message::getRead, 0));
    }

    public void markRead(Long id, Long userId) {
        messageMapper.update(null, new LambdaUpdateWrapper<Message>()
                .eq(Message::getId, id)
                .eq(Message::getUserId, userId)
                .eq(Message::getRead, 0)
                .set(Message::getRead, 1));
    }

    public void markAllRead(Long userId) {
        messageMapper.update(null, new LambdaUpdateWrapper<Message>()
                .eq(Message::getUserId, userId)
                .eq(Message::getRead, 0)
                .set(Message::getRead, 1));
    }
}
