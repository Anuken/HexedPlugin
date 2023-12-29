package hexed;

import arc.Core;

import java.util.HashMap;

public class MMR_config { // this is for local version ( not used )
    private static MMR_config instance;

    // Data storage variable
    private HashMap<String, Long> playerMMRs = new HashMap<>();

    // Private constructor to prevent instantiation
    private MMR_config() {
        // Read data during initialization
        loadData();
    }

    // Public method to get the instance
    public static synchronized MMR_config getInstance() {
        if (instance == null) {
            instance = new MMR_config();
        }
        return instance;
    }

    // Method to read data (e.g., from a file)
    private void loadData() {
        this.playerMMRs = Core.settings.getJson("playerMMRs", HashMap.class, Long.class, HashMap::new);
    }

    // Method to write data
    public void write1Data(String uuid,Long newMMR) {
        this.playerMMRs.put(uuid,newMMR);
    }
    public void saveDataToConfig(){
        Core.settings.putJson("playerMMRs",String.class,this.playerMMRs);
    }

    // Method to get current data
    public Long getPlayerMMR(String uuid) {
        Long mmr = this.playerMMRs.get(uuid);
        if (mmr!=null){
            return mmr;
        }else{
            this.playerMMRs.put(uuid,1000L);
            return 1000L;
        }
    }
}
