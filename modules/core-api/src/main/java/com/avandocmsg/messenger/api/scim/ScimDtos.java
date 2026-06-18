package com.avandocmsg.messenger.api.scim;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
record ScimEmail(
    @JsonProperty("value") String value,
    @JsonProperty("primary") Boolean primary
) {}

@JsonInclude(JsonInclude.Include.NON_NULL)
record ScimMeta(
    @JsonProperty("resourceType") String resourceType,
    @JsonProperty("location") String location
) {}

@JsonInclude(JsonInclude.Include.NON_NULL)
record ScimUserResource(
    @JsonProperty("schemas") List<String> schemas,
    @JsonProperty("id") String id,
    @JsonProperty("externalId") String externalId,
    @JsonProperty("userName") String userName,
    @JsonProperty("displayName") String displayName,
    @JsonProperty("active") Boolean active,
    @JsonProperty("emails") List<ScimEmail> emails,
    @JsonProperty("meta") ScimMeta meta
) {}

@JsonInclude(JsonInclude.Include.NON_NULL)
record ScimListResponse(
    @JsonProperty("schemas") List<String> schemas,
    @JsonProperty("totalResults") int totalResults,
    @JsonProperty("startIndex") int startIndex,
    @JsonProperty("itemsPerPage") int itemsPerPage,
    @JsonProperty("Resources") List<ScimUserResource> resources
) {}
