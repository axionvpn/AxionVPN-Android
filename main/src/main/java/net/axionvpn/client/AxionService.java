package net.axionvpn.client;

import retrofit.RestAdapter;
import retrofit.http.Field;
import retrofit.http.FormUrlEncoded;
import retrofit.http.GET;
import retrofit.http.POST;

public class AxionService {

    private static final String url = "https://axionvpn.net/";

    private static AxionApi service;
    private static String username;
    private static String password;

    static {
        RestAdapter restAdapter = new RestAdapter.Builder()
                .setEndpoint(url)
                .build();
        //restAdapter.setLogLevel(RestAdapter.LogLevel.FULL);
        service = restAdapter.create(AxionApi.class);
    }

    public static void setLoginInfo(String user, String pass) {
        username = user;
        password = pass;
    }

    public static RespGetConnInfo getConnInfo() {
        return service.getInfo(username,password);
    }
    public static VpnDesc [] getRegions() {
        RespGetVpns resp = service.getVpns();
        for (VpnDesc vpn : resp.vpns)
            LogManager.d(String.format("VPN %d: %s",vpn.id, vpn.geo_area));
        return resp.vpns;
    }

    public static String getConfigForRegion(int regionId) {
        RespGetVpnConfig resp = service.getConfig(username,password,regionId);
        return resp.conf;
    }
}

class VpnDesc {
    int id;
    String geo_area;
    @Override
    public String toString() {
        return geo_area;
    }
}
class RespGetVpns {
    VpnDesc [] vpns;
}
class RespGetVpnConfig {
    int result;
    String conf;
}
class RespGetConnInfo {
    int result;
    String acc_type;
    String ip_address;
}
interface AxionApi {
    @GET("/api/get-vpns/")
    RespGetVpns getVpns();
    @FormUrlEncoded
    @POST("/api/get-config/")
    RespGetVpnConfig getConfig(
        @Field("username") String username,
        @Field("password") String password,
        @Field("id") Integer id
    );
    @FormUrlEncoded
    @POST("/api/get-info/")
    RespGetConnInfo getInfo(
        @Field("username") String username,
        @Field("password") String password
    );
}