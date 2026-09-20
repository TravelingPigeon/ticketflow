package com.example.ticketflow.ticket.comment.domain.enums;

/** 评论类型。 */
public enum CommentType {

    /** 公开回复：企业成员与对应客户都可见，客户回复只能用它 */
    PUBLIC_REPLY,

    /** 内部备注：说给同事看的话，客户看不到 */
    INTERNAL_NOTE
}