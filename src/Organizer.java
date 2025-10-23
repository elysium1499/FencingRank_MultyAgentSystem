import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.WakerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;
import java.util.*;
import javax.swing.SwingUtilities;

public class Organizer extends Agent {
    private Map<String,String[]> registeredFencer = new HashMap<>(); // fencerAIDname [Name, rank]
    public Map<Integer, List<String[]>> pools = new HashMap<>(); // poolNumber [AIDNameFencer]
    private List<String[]> poolReferees = new ArrayList<>(); // [AIDNameReferee NameReferee]
    private Map<String, int[]> fencerStats = new HashMap<>(); // FencerAID [numVictory, SD, SR]
    private List<String[]> eliminationBout = new ArrayList<>(); // [FencerAID, FencerAIDAdversary]
    
    private boolean registrationClosed = false;
    private OrganizerBackboard gui; //Gui


    protected void setup() {
        SwingUtilities.invokeLater(() -> gui = new OrganizerBackboard());
        addBehaviour(new Registrations());
        addBehaviour(new FightResults());
    }

    private class Registrations extends Behaviour {
        private long time = System.currentTimeMillis();

        public void action() {
            boolean receivedAny = false;
            ACLMessage mex;
            
            // wait to receive all the fencer registration
            while ((mex = receive()) != null) {
                if (mex.getPerformative() == ACLMessage.INFORM) {
                    time = System.currentTimeMillis();  // reset timer

                    String[] fencer = mex.getContent().split(",");
                    registeredFencer.put(fencer[0], new String[]{fencer[1], fencer[2]});
                    fencerStats.put(fencer[0], new int[]{0, 0, 0});

                    // Send confirmation reply
                    ACLMessage reply = mex.createReply();
                    reply.setPerformative(ACLMessage.CONFIRM);
                    send(reply);

                    receivedAny = true;
                }
            }

            // If i dont receive nothing for 10 second i clore registration
            if (!receivedAny) {
                long now = System.currentTimeMillis();
                if (!registrationClosed && now - time >= 10_000) {
                    System.out.println("Closing registration.");
                    generatePools();
                    registrationClosed = true;
                } else {
                    block(1000);
                }
            }
            
            if(registrationClosed == true){
                //find referee
                findAndAssignReferees(pools.size());
            }
        }

        @Override
        public boolean done() {
            return registrationClosed;
        }

        private void generatePools() {
            // Order the fencer ascending for key (rank)
            List<Map.Entry<String, String[]>> orderedFencers = new ArrayList<>(registeredFencer.entrySet());
            orderedFencers.sort(Comparator.comparingInt(e -> Integer.parseInt(e.getValue()[1])));

            //compute the number of pool
            int numPools = (int) Math.ceil((double) orderedFencers.size() / 7); //max pool dimension must be 7
            
            for (int i = 1; i <= numPools; i++) pools.put(i, new ArrayList<>());

            //assign fencer to pool in balancing way
            int index = 0;
            for (Map.Entry<String, String[]> fencer : orderedFencers) {
                String fencerName = fencer.getValue()[0];
                String fencerAID = fencer.getKey();
                int poolNumber = (index % numPools) + 1;

                pools.get(poolNumber).add(new String[]{fencerName, fencerAID});

                index++;
            }
            
            //open and implement the gui
            if (gui != null) {
                SwingUtilities.invokeLater(() -> gui.displayPools(pools));
            }
        }

        private void findAndAssignReferees(int numPools) {
            try {
                DFAgentDescription template = new DFAgentDescription();
                ServiceDescription refereeService = new ServiceDescription();
                refereeService.setType("referee");
                template.addServices(refereeService);

                // Look all referee available
                DFAgentDescription[] referees = DFService.search(myAgent, template);
                Map<AID, String> availableReferees = new HashMap<>(); 

                // Send request at all referee
                for (DFAgentDescription dfad : referees) {
                    AID refereeAID = dfad.getName();
                    ACLMessage req = new ACLMessage(ACLMessage.REQUEST);
                    req.addReceiver(refereeAID);
                    send(req);
                }

                // Wait answer for 10 second
                long startTime = System.currentTimeMillis();
                while (System.currentTimeMillis() - startTime < 10000) {
                    ACLMessage reply = receive();
                    if (reply != null && reply.getPerformative() == ACLMessage.AGREE) {
                        AID refereeAID = reply.getSender();
                        String Name = reply.getContent();
                        availableReferees.put(refereeAID, Name);
                    }
                }

                // Chose n random referee
                List<Map.Entry<AID, String>> availableReferee = new ArrayList<>(availableReferees.entrySet());
                Collections.shuffle(availableReferee);
                List<Map.Entry<AID, String>> chosenReferees = null;

                if(availableReferee.size() > pools.size())
                    chosenReferees = availableReferee.subList(0, Math.min(numPools, availableReferee.size()));
                else chosenReferees = availableReferee;
                 
                //answer at referee the assigned pool
                ///////////////////////////////////////////////////////////////////////////////////////////////
                int poolCount = 0, refereeCount = 0;
                while(poolCount < numPools){
                //for (int j = 0; j < numPools; j++) {
                    // AID refereeAIDName = chosenReferees.get(j).getKey();
                    // String refereeName = chosenReferees.get(j).getValue();
                    AID refereeAIDName = chosenReferees.get(refereeCount).getKey();
                    String refereeName = chosenReferees.get(refereeCount).getValue();

                    poolReferees.add(new String[]{refereeAIDName.getLocalName(), refereeName});

                    //answer at referee
                    ACLMessage mex = new ACLMessage(ACLMessage.INFORM);
                    mex.addReceiver(refereeAIDName);
                    mex.setConversationId("PoolComposition");

                    StringBuilder contentBuilder = new StringBuilder();
                    //contentBuilder.append(j + 1); // pool number in head
                    contentBuilder.append(poolCount +1); // pool number in head
                    
                    // List<String[]> fencersInPool = pools.get(j + 1);
                    List<String[]> fencersInPool = pools.get(poolCount+1);
                    for (String[] fencer : fencersInPool) {
                        contentBuilder.append(",").append(fencer[1]); 
                    }

                    mex.setContent(contentBuilder.toString()); //all body are AID of fencer
                    send(mex);
                    
                    // int poolNumber = j + 1;
                    int poolNumber = poolCount + 1;
                    if (gui != null) {
                        final String finalRefereeName = refereeName;
                        SwingUtilities.invokeLater(() -> gui.updatePoolReferee(poolNumber, finalRefereeName));
                    }

                    poolCount++;
                    refereeCount++;

                    if(refereeCount == chosenReferees.size()){
                        try {
                            Thread.sleep(1000); // Wait 1 seconds
                            refereeCount = 0;
                        } catch (InterruptedException error) {
                            error.printStackTrace();
                        }
                    }
                }
            } catch (FIPAException e) {
                e.printStackTrace();
            }
        }
    }

    int numPoolComplete=0;
    private List<String[]> eliminationBoutName = new ArrayList<>();
    List<String> winners =  new ArrayList<>();

    private class FightResults extends CyclicBehaviour {
        public void action() {
            ACLMessage mex = receive();
            if (mex != null){
                String conversationId = mex.getConversationId();
                if(mex.getPerformative() == ACLMessage.INFORM && "resultBout".equals(conversationId)) {
                    String[] parts = mex.getContent().split(",");
                    if (parts.length == 5) {
                        int poolNumber = Integer.parseInt(parts[0]);
                        String fencer1AID = parts[1];
                        String score1 = parts[2];
                        String fencer2AID = parts[3];
                        String score2 = parts[4];

                        System.out.println("Result: " + poolNumber + ", " + registeredFencer.get(fencer1AID)[0] + " (" + score1 + ") vs " + registeredFencer.get(fencer2AID)[0] + " (" + score2 + ")");

                        // Aggiorna statistiche fencer1
                        int[] stats1 = fencerStats.get(fencer1AID);
                        if (Integer.parseInt(score1) == 5) stats1[0]++;
                        stats1[1] += Integer.parseInt(score1);        
                        stats1[2] += Integer.parseInt(score2);          

                        // Aggiorna statistiche fencer2
                        int[] stats2 = fencerStats.get(fencer2AID);
                        if (Integer.parseInt(score2) == 5) stats2[0]++;
                        stats2[1] += Integer.parseInt(score2);
                        stats2[2] += Integer.parseInt(score1);

                        fencerStats.put(fencer1AID, stats1);
                        fencerStats.put(fencer2AID, stats2);

                        if (gui != null) {
                            SwingUtilities.invokeLater(() ->{
                                gui.updateScore(poolNumber, registeredFencer.get(fencer1AID)[0], score1, registeredFencer.get(fencer2AID)[0], score2);
                            });
                        }
                    }
                }else if(mex.getPerformative() == ACLMessage.INFORM && "poolEnd".equals(conversationId)) {
                    numPoolComplete ++;
                    if (numPoolComplete==pools.size()) { //wait that all pool are end
                        System.out.println("Pools are end");
                        if (gui != null) {
                            for (Map.Entry<Integer, List<String[]>> poolEntry : pools.entrySet()) {
                                int poolNumber = poolEntry.getKey();
                                for (String[] fencerInfo : poolEntry.getValue()) {
                                    String fencerName = fencerInfo[0];
                                    String fencerAID = fencerInfo[1];
                                    
                                    int[] stats = fencerStats.get(fencerAID);
                                    if (stats != null) {
                                        int victories = stats[0];
                                        int hitsGiven = stats[1];
                                        int hitsReceived = stats[2];
                                        
                                        SwingUtilities.invokeLater(() -> {
                                            gui.updatePoolStats(poolNumber, fencerName, victories, hitsGiven, hitsReceived);
                                        });
                                    }
                                }
                            }
                        } 
                        eliminateWeakFencers();
                        calculateStartTree();
                    }
                }else if(mex.getPerformative() == ACLMessage.INFORM && "resultDirectBout".equals(conversationId)){
                    String parts = mex.getContent();
                    if (!winners.contains(parts)) {
                        winners.add(parts);
                    }

                    if(winners.size() == eliminationBout.size()){
                        //order the winner for the direct tree structure
                        List<String> suppSort = new ArrayList<>();
                        for (String[] match : eliminationBout) {
                            suppSort.addAll(Arrays.asList(match));
                        }
                        winners.sort(Comparator.comparingInt(suppSort::indexOf));

                        //there are any direct to finish
                        if (eliminationBout.size() > 1){
                            eliminationBout.clear();
                            eliminationBoutName.clear();
                            
                            while(!winners.isEmpty()){
                                eliminationBout.add(new String[]{winners.get(0), winners.get(1)});
                                
                                //recover name for GUI tree
                                String fencer1AID = winners.get(0);
                                String fencer2AID = winners.get(1);
                                eliminationBoutName.add(new String[]{registeredFencer.get(fencer1AID)[0], registeredFencer.get(fencer2AID)[0]});

                                winners.remove(1);
                                winners.remove(0);
                            }

                            if (gui != null) {
                                final List<String[]> nextRoundBouts = new ArrayList<>(eliminationBoutName);
                                SwingUtilities.invokeLater(() -> gui.displayEliminationRound(nextRoundBouts));
                            }

                            System.out.println("-------------------------");
                            SendDirect();

                        } else if (!winners.isEmpty() && winners.size()==1){
                            //i finish the direct, there is the winner
                            System.out.println("TOURNAMENT WINNER: " + registeredFencer.get(winners.get(0))[0]);
                            if (gui != null) {
                                List<String[]> finalWinnerRound = new ArrayList<>();
                                finalWinnerRound.add(new String[]{registeredFencer.get(winners.get(0))[0], null});

                                SwingUtilities.invokeLater(() -> {
                                    gui.displayEliminationRound(finalWinnerRound);
                                    gui.displayFinalWinner(registeredFencer.get(winners.get(0))[0]);
                                });
                            }

                            //close the last two fencer
                            shutdownAllFencers();

                            //close all referee
                            shutdownAllReferees();
                            
                            //close the organizer
                            System.out.println("Organizer shutting down");
                            addBehaviour(new WakerBehaviour(myAgent, 5000) {
                                @Override
                                protected void onWake() {
                                    myAgent.doDelete();
                                }
                            });
                        }
                    }
                }
            } else {
                block();
            }
        }

        private void eliminateWeakFencers() {
            // Remove fencer with low victory (<=2)
            for (Map.Entry<String, int[]> fencerFild : new HashMap<>(fencerStats).entrySet()) {
                String fencerAID = fencerFild.getKey();
                int[] stats = fencerFild.getValue();
                int victories = stats[0];

                if (victories <= 2) {
                    System.out.println("Eliminating: " + registeredFencer.get(fencerAID)[0]);

                    ACLMessage mex = new ACLMessage(ACLMessage.INFORM);
                    mex.setConversationId("elimination");
                    mex.addReceiver(new AID(fencerAID, AID.ISLOCALNAME));
                    send(mex);      

                    fencerStats.remove(fencerAID);

                    for (List<String[]> poolFencerList : pools.values()) {
                        poolFencerList.removeIf(f -> f[1].equals(fencerAID));
                    }
                }
            }

            // If the fencer number is not power of 2 i remove the baddests
            int totalFencers = fencerStats.size();

            // compute the highest power of 2 (totalFencers)
            int targetSize = 1;
            while (targetSize * 2 <= totalFencers) {
                targetSize *= 2;
            }

            int toEliminate = totalFencers - targetSize;

            if (toEliminate > 0) {
                for (int i = 0; i < toEliminate; i++) {
                    String worstFencerID = null;
                    int worstFencerVictories = Integer.MAX_VALUE;
                    int worstFencerSR = Integer.MIN_VALUE;

                    // Find baddest fencer
                    for (Map.Entry<String, int[]> fencerFild : fencerStats.entrySet()) {
                        String fencerAID = fencerFild.getKey();
                        int[] stats = fencerFild.getValue();
                        int victories = stats[0];
                        int SR = stats[2];

                        if (worstFencerID == null || victories < worstFencerVictories || (victories == worstFencerVictories && SR > worstFencerSR)) {
                            worstFencerID = fencerAID;
                            worstFencerVictories = victories;
                            worstFencerSR = SR;
                        }
                    }

                    if (worstFencerID != null && fencerStats.containsKey(worstFencerID)) {
                        final String eliminateID = worstFencerID;

                        System.out.println("Eliminate: " + eliminateID);

                        ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
                        msg.setConversationId("elimination");
                        msg.addReceiver(new AID(eliminateID, AID.ISLOCALNAME));
                        send(msg);

                        fencerStats.remove(eliminateID);

                        for (List<String[]> poolFencerList : pools.values()) {
                            poolFencerList.removeIf(f -> f[1].equals(eliminateID));
                        }
                    }
                }
            }
        }

        private void calculateStartTree() {
            List<Object[]> fencersStatsList = new ArrayList<>();

            for (Map.Entry<String, int[]> fencerField : fencerStats.entrySet()) {
                String fencerAID = fencerField.getKey();
                int[] stats = fencerField.getValue();
                fencersStatsList.add(new Object[]{stats[0], stats[1] - stats[2], stats[1], stats[2], fencerAID}); //numVictory, SD-SR, SD, SR, fencerID
            }

            fencersStatsList.sort((f1, f2) -> {
                // sort follow rank precedence rules
                if ((int) f2[0] != (int) f1[0]) 
                    return Integer.compare((int) f2[0], (int) f1[0]); //who do more victory
                if ((int) f2[1] != (int) f1[1]) 
                    return Integer.compare((int) f2[1], (int) f1[1]); //who do more point 
                if ((int) f2[2] != (int) f1[2]) 
                    return Integer.compare((int) f2[2], (int) f1[2]); //who do more SD
                return Integer.compare((int) f1[3], (int) f2[3]); //who have less SR
            });

            
            List<String> orderedFencers = new ArrayList<>();
            for (Object[] f : fencersStatsList) {
                String fencerAID = (String) f[4];
                orderedFencers.add(fencerAID);
            }

            // for rule who do better go with who do badest
            int n = orderedFencers.size();
            for (int i = 0; i < n / 2; i++) {
                eliminationBout.add(new String[]{orderedFencers.get(i), orderedFencers.get(n - 1 - i)});
            }

            for (String[] bout : eliminationBout) {
                String fencer1Name = registeredFencer.get(bout[0])[0];
                String fencer2Name = registeredFencer.get(bout[1])[0]; 

                eliminationBoutName.add(new String[]{fencer1Name, fencer2Name});
            }

            System.out.println("------------------------------");

            if (gui != null) {
                final List<String[]> initialBouts = new ArrayList<>(eliminationBoutName);
                SwingUtilities.invokeLater(() -> gui.displayEliminationRound(initialBouts));
            }
            SendDirect();
        }

        private void SendDirect() {
            //referee assegnation 
            int refereeIndex = 0;
            for (String[] bout : eliminationBout) {
                String AIDrefereeName = poolReferees.get(refereeIndex)[0];  // safe now since checked above

                ACLMessage mex = new ACLMessage(ACLMessage.INFORM);
                mex.addReceiver(new jade.core.AID(AIDrefereeName, jade.core.AID.ISLOCALNAME));
                mex.setConversationId("directBout");
                mex.setContent(bout[0] + "," + bout[1]);
                send(mex);

                //the number of referee is less than number of direct bout
                refereeIndex++;
                if(refereeIndex == poolReferees.size()){
                    try {
                        Thread.sleep(5000); // Wait 5 seconds
                        refereeIndex = 0;
                    } catch (InterruptedException error) {
                        error.printStackTrace();
                    }
                }
            }
        }
    
        private void shutdownAllFencers() {
            for (String fencerAIDName : registeredFencer.keySet()) {
                AID fencerAID = new AID(fencerAIDName, AID.ISLOCALNAME);
                ACLMessage shutdownMex = new ACLMessage(ACLMessage.INFORM);
                shutdownMex.setConversationId("elimination");
                shutdownMex.addReceiver(fencerAID);
                send(shutdownMex);
            }

            System.out.println("Fencers shutting down");
        }
        
        private void shutdownAllReferees() {
            try {
                DFAgentDescription template = new DFAgentDescription();
                ServiceDescription refereeService = new ServiceDescription();
                refereeService.setType("referee");
                template.addServices(refereeService);

                // find all registered fencer
                DFAgentDescription[] referees = DFService.search(myAgent, template);

                for (DFAgentDescription dfad : referees) {
                    AID refereeAID = dfad.getName();
                    ACLMessage shutdownMex = new ACLMessage(ACLMessage.INFORM);
                    shutdownMex.setConversationId("discharged");
                    shutdownMex.addReceiver(refereeAID);
                    send(shutdownMex);
                }

                System.out.println("Referees shutting down");

            } catch (FIPAException e) {
                e.printStackTrace();
            }
        }
    }
}