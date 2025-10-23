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
            Runtime rt = Runtime.instance();
            Profile profile = new ProfileImpl();
            profile.setParameter(Profile.GUI, "true");
            ContainerController cc = rt.createMainContainer(profile);

            //create organizer agent
            AgentController organizer = cc.createNewAgent("organizer", Organizer.class.getName(), null);
            organizer.start();

            // create fencer agent
            List<String[]> fencers = loadCSV("fencers.csv");
            Set<String> fencerIdentitySet = new HashSet<>();

            // save fencer identity because cant a person do fencer and referee at same time
            for (int i = 0; i < fencers.size(); i++) {
                String name = fencers.get(i)[0].trim().toLowerCase();
                String rank = fencers.get(i)[6].trim();
                fencerIdentitySet.add(name + "-" + rank);

                String agentName = "fencer" + (i + 1);
                AgentController Fencer = cc.createNewAgent(agentName, Fencer.class.getName(), fencers.get(i));
                Fencer.start();
            }

            // create referee agent
            List<String[]> referees = loadCSV("referee.csv");
            int refereeCount = 1;

            for (String[] refereeData : referees) {
                String agentName = "referee" + refereeCount++;
                AgentController Referee = cc.createNewAgent(agentName, Referee.class.getName(), refereeData);
                Referee.start();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    // Read file CSV
    private static List<String[]> loadCSV(String filePath) throws IOException {
        List<String[]> fencers = new ArrayList<>();
        BufferedReader br = new BufferedReader(new FileReader(filePath));
        String line;
        boolean firstLine = true;

        while ((line = br.readLine()) != null) {
            if (firstLine) {
                firstLine = false; // skip first line
                continue;
            }
            String[] values = line.split(",");
            fencers.add(values);
        }
        br.close();
        return fencers;
    }

}
