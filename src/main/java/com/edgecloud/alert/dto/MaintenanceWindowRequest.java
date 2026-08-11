package com.edgecloud.alert.dto;
import java.time.Instant; import java.util.UUID; import com.edgecloud.alert.entity.MaintenanceScopeType; import jakarta.validation.constraints.*;
public record MaintenanceWindowRequest(@NotNull MaintenanceScopeType scopeType,UUID serviceId,@Size(max=128) String deviceId,@NotBlank @Size(max=200) String name,@NotBlank @Size(max=1000) String reason,@NotNull Instant startsAt,@NotNull Instant endsAt,boolean enabled){}
