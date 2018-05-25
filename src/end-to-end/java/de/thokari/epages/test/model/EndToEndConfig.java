package de.thokari.epages.test.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import de.thokari.epages.app.model.Config;

public class EndToEndConfig extends Config {

    public String epagesHostname;

    public Boolean createShop;

    public String shopName;

    public Boolean createPrivateApp;

    public String token;

    @JsonCreator
    public EndToEndConfig(
        @JsonProperty("epagesHostname") String epagesHostname,
        @JsonProperty("createShop") Boolean createShop,
        @JsonProperty("shopName") String shopName,
        @JsonProperty("createPrivateApp") Boolean createPrivateApp,
        @JsonProperty("token") String token) {

        this.epagesHostname = validate("epagesHostname", overrideFromEnv("EPAGES_HOSTNAME", epagesHostname));
        this.createShop = validate("createShop", overrideFromEnv("CREATE_SHOP", createShop, Boolean.class));
        this.shopName = validate("shopName", overrideFromEnv("SHOP_NAME", shopName));
        this.createPrivateApp = validate("createPrivateApp",
            overrideFromEnv("CREATE_PRIVATE_APP", createPrivateApp, Boolean.class));
        this.token = validate("token", overrideFromEnv("TOKEN", token));
    }
}
