package cn.bugstack.mcp.server.csdn.infrastructure.gateway.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Collections;
import java.util.List;

@Data
public class ArticleRequestDTO {

    private String title;
    private String markdowncontent;
    private String content;
    private String readType = "public";
    private String level = "0";
    private String tags;
    private Integer status = 2;
    private String categories = "后端";
    private String type = "original";
    private String original_link = "";
    private Boolean authorized_status = true;

    private String description;

    private String resource_url = "";
    private String not_auto_saved = "0";
    private String source = "pc_mdeditor";
    private List<String> cover_images = Collections.emptyList();
    private Integer cover_type = 0;
    private Integer is_new = 1;
    private Integer vote_id = 0;
    private String resource_id = "";
    private String pubStatus = "draft";
    private Integer sync_git_code = 0;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getMarkdowncontent() {
        return markdowncontent;
    }

    public void setMarkdowncontent(String markdowncontent) {
        this.markdowncontent = markdowncontent;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getReadType() {
        return readType;
    }

    public void setReadType(String readType) {
        this.readType = readType;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getCategories() {
        return categories;
    }

    public void setCategories(String categories) {
        this.categories = categories;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getOriginal_link() {
        return original_link;
    }

    public void setOriginal_link(String original_link) {
        this.original_link = original_link;
    }

    public Boolean getAuthorized_status() {
        return authorized_status;
    }

    public void setAuthorized_status(Boolean authorized_status) {
        this.authorized_status = authorized_status;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getResource_url() {
        return resource_url;
    }

    public void setResource_url(String resource_url) {
        this.resource_url = resource_url;
    }

    public String getNot_auto_saved() {
        return not_auto_saved;
    }

    public void setNot_auto_saved(String not_auto_saved) {
        this.not_auto_saved = not_auto_saved;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public List<String> getCover_images() {
        return cover_images;
    }

    public void setCover_images(List<String> cover_images) {
        this.cover_images = cover_images;
    }

    public Integer getCover_type() {
        return cover_type;
    }

    public void setCover_type(Integer cover_type) {
        this.cover_type = cover_type;
    }

    public Integer getIs_new() {
        return is_new;
    }

    public void setIs_new(Integer is_new) {
        this.is_new = is_new;
    }

    public Integer getVote_id() {
        return vote_id;
    }

    public void setVote_id(Integer vote_id) {
        this.vote_id = vote_id;
    }

    public String getResource_id() {
        return resource_id;
    }

    public void setResource_id(String resource_id) {
        this.resource_id = resource_id;
    }

    public String getPubStatus() {
        return pubStatus;
    }

    public void setPubStatus(String pubStatus) {
        this.pubStatus = pubStatus;
    }

    public Integer getSync_git_code() {
        return sync_git_code;
    }

    public void setSync_git_code(Integer sync_git_code) {
        this.sync_git_code = sync_git_code;
    }
}