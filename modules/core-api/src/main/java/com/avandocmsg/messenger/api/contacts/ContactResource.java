package com.avandocmsg.messenger.api.contacts;

import com.avandocmsg.messenger.api.contacts.dto.AddContactRequest;
import com.avandocmsg.messenger.api.params.CurrentUserId;
import com.avandocmsg.messenger.api.params.UuidParams;
import com.avandocmsg.messenger.api.contacts.dto.ImportContactsRequest;
import com.avandocmsg.messenger.api.contacts.dto.ImportContactsResponse;
import com.avandocmsg.messenger.common.dto.ApiError;
import com.avandocmsg.messenger.common.i18n.UserMessageSource;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

@Path("/v1/contacts")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ContactResource {

    private final ContactService contactService;
    private final UserMessageSource messages;

    @Inject
    public ContactResource(ContactService contactService, UserMessageSource messages) {
        this.contactService = contactService;
        this.messages = messages;
    }

    @GET
    public Response list(@Context SecurityContext securityContext) {
        var userId = CurrentUserId.uuid(securityContext);
        var contacts = contactService.list(userId);
        return Response.ok(contacts).build();
    }

    @POST
    public Response add(AddContactRequest request, @Context SecurityContext securityContext) {
        if (request == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ApiError(400, messages.get("error.contact.body_required")))
                .build();
        }
        var userId = CurrentUserId.uuid(securityContext);
        var contactUserId = UuidParams.required(request.userId(), "user_id");
        var added = contactService.add(userId, contactUserId);
        if (!added) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ApiError(400, messages.get("error.contact.cannot_add")))
                .build();
        }
        return Response.status(Response.Status.CREATED).build();
    }

    @DELETE
    @Path("/{contactId}")
    public Response remove(@PathParam("contactId") String contactId,
                           @Context SecurityContext securityContext) {
        var userId = CurrentUserId.uuid(securityContext);
        var removed = contactService.remove(userId, UuidParams.required(contactId, "contact_id"));
        if (!removed) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new ApiError(404, messages.get("error.contact.not_found")))
                .build();
        }
        return Response.noContent().build();
    }

    @POST
    @Path("/import")
    public Response importContacts(ImportContactsRequest request,
                                    @Context SecurityContext securityContext) {
        if (request.phoneHashes() == null || request.phoneHashes().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ApiError(400, messages.get("error.contact.phone_hashes_required")))
                .build();
        }
        var userId = CurrentUserId.uuid(securityContext);
        if (request.phoneHashes().size() > 1000) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ApiError(400, messages.get("error.contact.max_hashes")))
                .build();
        }
        var result = contactService.importByPhoneHashes(userId, request.phoneHashes());
        return Response.ok(result).build();
    }
}
