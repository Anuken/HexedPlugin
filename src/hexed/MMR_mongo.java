package hexed;

import arc.Core;
import arc.struct.Seq;
import arc.util.Log;
import arc.util.Strings;
import com.mongodb.bulk.BulkWriteResult;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.BulkWriteOptions;
import com.mongodb.client.model.WriteModel;
import mindustry.gen.Player;
import org.bson.Document;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.mongodb.client.model.Filters.eq;


public class MMR_mongo {
    public MongoDatabase db;

    public MongoCollection<Document> hexdataV7;
    public MongoCollection<Document> duuid1;

    public MMR_mongo(String mongoURL) {
        Logger mongoLogger = Logger.getLogger("org.mongodb.driver");
        mongoLogger.setLevel(Level.SEVERE);
        MongoClient mongoClient = MongoClients.create(mongoURL);
        db = mongoClient.getDatabase("AlexMindustry");
        hexdataV7 = db.getCollection("hexdataV7");
        duuid1 = db.getCollection("duuid1");
    }



    public Long read_hexdataV7(Player p, String muuid){
        try {
            Document hexv7doc = hexdataV7.find(eq("muuid", muuid)).first();
            if (hexv7doc == null) { // brand-new acc, add new doc
                Document doc = new Document("muuid", muuid)
                        .append("musername", p.name())
                        .append("date", new Date())
                        .append("currMMR", 1000L)
                        .append("dates", new ArrayList<>())
                        .append("losswinrank", new ArrayList<>()) // 0 for lose, then followed by rank
                        .append("hexescontrolled", new ArrayList<>())
                        .append("MMRs", new ArrayList<>());
                hexdataV7.insertOne(doc);
                return 1000L;
            } else {
                return hexv7doc.getLong("currMMR");
            }
        } catch (Exception e) {
            Log.info("failed to send exp gains" + e);
            return 1000L;
        }
    }
    public void bulkWritehexdataV7(List<WriteModel<Document>> bulkOperations){
        BulkWriteResult result = hexdataV7.bulkWrite(bulkOperations, new BulkWriteOptions().ordered(false));
        final int modifiedCount = result.getModifiedCount();
        if (modifiedCount>0) {
            Log.info(modifiedCount + "mod counts" + bulkOperations.size());
        }
    }

//    public Document get_duuid1_doc(Player p) { // used for registering muuid to duuid, and setplayerdata
//        try {
//            Document res = duuid1.find(eq("muuid", p.uuid())).first();
//            return res;
//        } catch (Exception e) {
//            Call.infoMessage(p.con, "Network error, please reconnect.\nYou may use /hub to go to hub and then comeback\n" + e);
//            return null;
//        }
//    }


}
