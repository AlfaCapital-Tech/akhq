package org.akhq.models.accessmanagement;

import io.micronaut.serde.annotation.Serdeable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Who decides on an access request: a prefix owner, or a super admin when the target has no owner. */
@Serdeable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Approver {
    private String username;
    private String email;
}
