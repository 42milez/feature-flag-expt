package com.github.milez42.featureflags.approval;

import java.util.UUID;
import org.springframework.data.repository.CrudRepository;

public interface UpdateApprovalRepository extends CrudRepository<UpdateApprovalEntity, UUID> {}
