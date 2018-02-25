import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;

/**
 * Created by jchen on 24/02/2018.
 */
public class kmsREST {
    private static final String KMS_BASE_URI="http://node3.openstacklocal.com:9292";
    private static final String KMS_KEYTAB="/Users/jchen/keyadmin.keytab";
    private static final String KMS_PRINCIPAL="keyadmin@HWX.COM";
    private static KerberosHttpClient kerberosHttpClient = new KerberosHttpClient(KMS_KEYTAB, KMS_PRINCIPAL, ServiceNameType.HOST_BASED);

    public static void main(String[] args) {

        String result;
        //add key
        result=KMSREST_addKey();
        if (result!=null) {
            System.out.println("Result For add Keys: " + result);
        }

        //get key
        result=KMSREST_getKeys();
        if (result!=null) {
            System.out.println("Result For Get Keys: " + result);
        }
    }

    private static String KMSREST_getKeys(){
        String result = null;
        try {
            result = kerberosHttpClient.executeGet(KMS_BASE_URI + "/kms/v1/keys/names");

            return result;
        } catch (Exception ex) {
            ex.printStackTrace();
            return null;
        }

    }

    private static String KMSREST_addKey(){
        String result = null;
        try {
            String reqestData="{\n" +
                    "  \"name\"        : \"test_key7\",\n" +
                    "  \"cipher\"      : \"aes\",\n" +
                    "  \"length\"      : 128, \n" +
                    "  \"material\"    : \"lksvIq3yy9Xxk4EZTfLv6g\", \n" +
                    "  \"description\" : \"test key6\"\n" +
                    "}";

            StringEntity entity = new StringEntity(reqestData,"utf-8");
            entity.setContentEncoding("UTF-8");
            entity.setContentType("application/json");
            HttpPost httpPost = new HttpPost(KMS_BASE_URI+"/kms/v1/keys");
            httpPost.setEntity(entity);

            result = kerberosHttpClient.executePost(httpPost);

            return result;
        } catch (Exception ex) {
            ex.printStackTrace();
            return null;
        }    }
}
