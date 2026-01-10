package com.idar.optisaas.model;

import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;
import com.idar.optisaas.security.TenantContext;

@MappedSuperclass
@Getter @Setter
@FilterDef(name = "branchFilter", parameters = @ParamDef(name = "branchId", type = Long.class))
@Filter(name = "branchFilter", condition = "branch_id = :branchId")
public abstract class BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        Long currentBranch = TenantContext.getCurrentBranch();
        if (this.branchId == null && currentBranch != null) {
            this.branchId = currentBranch;
        }
    }
}