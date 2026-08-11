package com.edgecloud.alert.dto;
import java.util.List; import com.edgecloud.alert.entity.Severity; import jakarta.validation.Valid; import jakarta.validation.constraints.*;
public record EscalationPolicyRequest(@NotBlank @Size(max=200) String name, boolean enabled, @NotEmpty List<@Valid Level> levels){
 public record Level(@Min(1) int levelNumber,@Min(1) long elapsedSeconds,@NotNull Severity targetSeverity,boolean enabled){}
}
