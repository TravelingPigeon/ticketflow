package com.example.ticketflow.ticket.comment.dto;

import com.example.ticketflow.ticket.comment.domain.enums.CommentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCommentRequest(

        @NotBlank(message = "评论内容不能为空")
        @Size(max = 5000, message = "评论内容不能超过5000个字符")
        String content,

        CommentType commentType
) {

        /**
         * 紧凑构造器：不传评论类型时按"公开回复"处理。
         *
         * <p>这是最常用的那一种，所以让它成为缺省值。
         * 放在这里而不是 Service：请求体缺字段时 Jackson 会传 null 进来，
         * 归一化一次，后面所有代码都不用再判断 null。</p>
         */
        public CreateCommentRequest {
                if (commentType == null) {
                        commentType = CommentType.PUBLIC_REPLY;
                }
        }

        /** 只带内容的便捷构造：等价于发表公开回复 */
        public CreateCommentRequest(String content) {
                this(content, CommentType.PUBLIC_REPLY);
        }
}