package me.jcloud.app.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UploadInitResponse {
    private UUID uploadId;
    private List<Integer> existingParts; // Resume field
}
