package de.thokari.epages.test.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import de.thokari.epages.app.model.Config;

public class EndToEndConfig extends Config {

    public String epagesHostname;

    public String token;

    public String shopName;

    @JsonCreator
    public EndToEndConfig(
        @JsonProperty("epagesHostname") String epagesHostname,
        @JsonProperty("shopName") String shopName,
        @JsonProperty("token") String token) {

        this.epagesHostname = validate("epagesHostname", overrideFromEnv("EPAGES_HOSTNAME", epagesHostname));
        this.token = validate("token", overrideFromEnv("TOKEN", token));
        this.shopName = validate("shopName", overrideFromEnv("SHOP_NAME", shopName));
    }
}
