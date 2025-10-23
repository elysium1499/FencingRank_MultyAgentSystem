import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.wrapper.AgentController;
import jade.wrapper.ContainerController;
import jade.core.Runtime;
import java.io.*;
import java.util.*;

public class Main {
    public static void main(String[] args) {
        try {
            Profile profile = new ProfileImpl();
            new ProfileImpl().setParameter(Profile.GUI, "true");
            ContainerController cc = Runtime.instance().createMainContainer(profile);

            //create organizer agent
            AgentController organizer = cc.createNewAgent("organizer", Organizer.class.getName(), null);
            organizer.start();

            // create fencer agent
            List<String[]> fencers = CSVreader("fencers.csv");
            Set<String> fencerId = new HashSet<>();

            // save fencer identity because cant a person do fencer and referee at same time
            for (int i = 0; i < fencers.size(); i++) {
                String fencerName = fencers.get(i)[0].trim().toLowerCase();
                String fencerRank = fencers.get(i)[6].trim();
                fencerId.add(fencerName + "-" + fencerRank);

                String agentName = "fencer" + (i + 1);
                AgentController Fencer = cc.createNewAgent(agentName, Fencer.class.getName(), fencers.get(i));
                Fencer.start();
            }

            // create referee agent
            List<String[]> referees = CSVreader("referee.csv");
            int refereeCount = 1;

            for (String[] refereeData : referees) {
                String agentName = "referee" + refereeCount++;
                AgentController Referee = cc.createNewAgent(agentName, Referee.class.getName(), refereeData);
                Referee.start();
            }

        } catch (Exception error) {
            error.printStackTrace();
        }
    }


    // Read file CSV
    private static List<String[]> CSVreader(String filePath) throws IOException {
        List<String[]> fields = new ArrayList<>();
        BufferedReader br = new BufferedReader(new FileReader(filePath));
        String line;
        boolean firstLine = true;

        // skip first line
        while ((line = br.readLine()) != null) {
            if (firstLine) {
                firstLine = false;
                continue;
            }
            String[] column = line.split(",");
            fields.add(column);
        }
        br.close();
        return fields;
    }

}
