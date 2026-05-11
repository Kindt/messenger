package com.avandocmsg.messenger.api.contacts.dto;

import java.util.List;

public record ImportContactsResponse(
    List<ContactResponse> contacts
) {}
