package com.acme.sportplatform.organizations.infrastructure.jpa;

import java.util.UUID;

import jakarta.persistence.*;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "venues")
public class VenueEntity {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "country_code", length = 8)
    private String countryCode;

    @Column(name = "city", length = 128)
    private String city;

    @Column(name = "address")
    private String address;

    @Column(name = "timezone", nullable = false, length = 64)
    private String timezone;

    @Column(name = "venue_meta", nullable = false, columnDefinition = "jsonb")
    private String venueMeta;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCountryCode() { return countryCode; }
    public void setCountryCode(String countryCode) { this.countryCode = countryCode; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }

    public String getVenueMeta() { return venueMeta; }
    public void setVenueMeta(String venueMeta) { this.venueMeta = venueMeta; }

}