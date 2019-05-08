
package org.mule.maven.exchange.model;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * The ExchangeMetadata Schema
 * <p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "projectId",
        "branchId",
        "commitId"
})
public class ExchangeMetadata {

    /**
     * The Projectid Schema
     * <p>
     * <p>
     * (Required)
     */
    @JsonProperty("projectId")
    private String projectId = "";
    /**
     * The Branchid Schema
     * <p>
     * <p>
     * (Required)
     */
    @JsonProperty("branchId")
    private String branchId = "";
    /**
     * The Commitid Schema
     * <p>
     * <p>
     * (Required)
     */
    @JsonProperty("commitId")
    private String commitId = "";
    @JsonIgnore
    private Map<String, Object> additionalProperties = new HashMap<String, Object>();

    /**
     * The Projectid Schema
     * <p>
     * <p>
     * (Required)
     */
    @JsonProperty("projectId")
    public String getProjectId() {
        return projectId;
    }

    /**
     * The Projectid Schema
     * <p>
     * <p>
     * (Required)
     */
    @JsonProperty("projectId")
    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public ExchangeMetadata withProjectId(String projectId) {
        this.projectId = projectId;
        return this;
    }

    /**
     * The Branchid Schema
     * <p>
     * <p>
     * (Required)
     */
    @JsonProperty("branchId")
    public String getBranchId() {
        return branchId;
    }

    /**
     * The Branchid Schema
     * <p>
     * <p>
     * (Required)
     */
    @JsonProperty("branchId")
    public void setBranchId(String branchId) {
        this.branchId = branchId;
    }

    public ExchangeMetadata withBranchId(String branchId) {
        this.branchId = branchId;
        return this;
    }

    /**
     * The Commitid Schema
     * <p>
     * <p>
     * (Required)
     */
    @JsonProperty("commitId")
    public String getCommitId() {
        return commitId;
    }

    /**
     * The Commitid Schema
     * <p>
     * <p>
     * (Required)
     */
    @JsonProperty("commitId")
    public void setCommitId(String commitId) {
        this.commitId = commitId;
    }

    public ExchangeMetadata withCommitId(String commitId) {
        this.commitId = commitId;
        return this;
    }

    @JsonAnyGetter
    public Map<String, Object> getAdditionalProperties() {
        return this.additionalProperties;
    }

    @JsonAnySetter
    public void setAdditionalProperty(String name, Object value) {
        this.additionalProperties.put(name, value);
    }

    public ExchangeMetadata withAdditionalProperty(String name, Object value) {
        this.additionalProperties.put(name, value);
        return this;
    }

}
