package ua.zhenya.cloudstorage.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.proxy.HibernateProxy;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Table(name = "users", indexes = {
        @Index(name = "idx_username", columnList = "username", unique = true)
})
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    @Column(unique = true, nullable = false)
    private String username;
    @Column(nullable = false)
    private String password;
    @Column(nullable = false)
    private LocalDateTime registeredAt;

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getEffectiveClass(this) != getEffectiveClass(obj)) return false;
        return getId() != null && getId().equals(((User) obj).getId());
    }

    @Override
    public final int hashCode() {
        return getEffectiveClass(this).hashCode();
    }

    private static Class<?> getEffectiveClass(Object o) {
        return o instanceof HibernateProxy
                ? ((HibernateProxy) o).getHibernateLazyInitializer().getClass()
                : o.getClass();
    }
}
