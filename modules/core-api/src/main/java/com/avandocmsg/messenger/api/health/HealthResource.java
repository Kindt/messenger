package com.avandocmsg.messenger.api.health;

import com.avandocmsg.messenger.api.config.AppConfig;
import com.avandocmsg.messenger.api.config.RedisProbe;
import com.avandocmsg.messenger.common.dto.HealthReadyResponse;
import com.avandocmsg.messenger.common.dto.HealthResponse;
import com.avandocmsg.messenger.core.port.NatsConnectionStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;

@Path("/v1/health")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Health", description = "Health check endpoints")
public class HealthResource {

    private static final Logger log = LoggerFactory.getLogger(HealthResource.class);

    private final AppConfig appConfig;
    private final DataSource dataSource;
    private final NatsConnectionStatus natsConnectionStatus;
    private final RedisProbe redisProbe;

    @Inject
    public HealthResource(
        AppConfig appConfig,
        DataSource dataSource,
        NatsConnectionStatus natsConnectionStatus,
        RedisProbe redisProbe
    ) {
        this.appConfig = appConfig;
        this.dataSource = dataSource;
        this.natsConnectionStatus = natsConnectionStatus;
        this.redisProbe = redisProbe;
    }

    @GET
    @Operation(summary = "Health check", description = "Returns service status and version")
    @ApiResponse(responseCode = "200", description = "Service is healthy",
        content = @Content(schema = @Schema(implementation = HealthResponse.class)))
    public HealthResponse health() {
        return new HealthResponse("ok", appConfig.version());
    }

    @GET
    @Path("ready")
    @Operation(summary = "Readiness", description = "Проверка подключения к PostgreSQL (для оркестраторов)")
    @ApiResponse(responseCode = "200", description = "БД доступна",
        content = @Content(schema = @Schema(implementation = HealthReadyResponse.class)))
    @ApiResponse(responseCode = "503", description = "БД недоступна",
        content = @Content(schema = @Schema(implementation = HealthReadyResponse.class)))
    public Response ready() {
        boolean dbOk = false;
        try (var conn = dataSource.getConnection();
             var st = conn.prepareStatement("SELECT 1");
             var rs = st.executeQuery()) {
            dbOk = rs.next();
        } catch (Exception e) {
            log.warn("readiness DB probe failed: {}", e.getMessage());
            dbOk = false;
        }
        boolean redisOk = redisProbe.ping();
        boolean natsOk = natsConnectionStatus.natsClientConnected();
        var body = new HealthReadyResponse(dbOk ? "ready" : "not_ready", appConfig.version(), dbOk, redisOk, natsOk);
        return dbOk ? Response.ok(body).build()
            : Response.status(Response.Status.SERVICE_UNAVAILABLE).entity(body).build();
    }
}
