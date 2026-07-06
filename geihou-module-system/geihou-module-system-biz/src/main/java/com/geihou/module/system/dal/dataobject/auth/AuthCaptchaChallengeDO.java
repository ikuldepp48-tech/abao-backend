package com.geihou.module.system.dal.dataobject.auth;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("auth_captcha_challenge")
public class AuthCaptchaChallengeDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("captcha_key")
    private String captchaKey;

    @TableField("answer_hash")
    private String answerHash;

    @TableField("expire_time")
    private LocalDateTime expireTime;

    @TableField("consumed_time")
    private LocalDateTime consumedTime;

    @TableField("create_time")
    private LocalDateTime createTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCaptchaKey() {
        return captchaKey;
    }

    public void setCaptchaKey(String captchaKey) {
        this.captchaKey = captchaKey;
    }

    public String getAnswerHash() {
        return answerHash;
    }

    public void setAnswerHash(String answerHash) {
        this.answerHash = answerHash;
    }

    public LocalDateTime getExpireTime() {
        return expireTime;
    }

    public void setExpireTime(LocalDateTime expireTime) {
        this.expireTime = expireTime;
    }

    public LocalDateTime getConsumedTime() {
        return consumedTime;
    }

    public void setConsumedTime(LocalDateTime consumedTime) {
        this.consumedTime = consumedTime;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
}
