import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.lang.acl.ACLMessage;

public class Fencer extends Agent {
    public String name;
    public int experience;
    public int speed;
    public int parry;
    public int stopTrust;
    public int feint;
    public int emotion;
    public int rank;

    protected void setup() {
        Object[] args = getArguments();
        if (args != null && args.length > 0) {
            name = (String) args[0];
            experience = Integer.parseInt((String) args[1]);
            speed = Integer.parseInt((String) args[2]);
            parry = Integer.parseInt((String) args[3]);
            stopTrust = Integer.parseInt((String) args[4]);
            feint = Integer.parseInt((String) args[5]);
            emotion = Integer.parseInt((String) args[6]);
            rank = Integer.parseInt((String) args[7]);

            // Behaviour to send the registration
            addBehaviour(new OneShotBehaviour() {
                public void action() {
                    ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
                    msg.addReceiver(new jade.core.AID("organizer", jade.core.AID.ISLOCALNAME));
                    msg.setContent(getAID().getLocalName() + "," + name + "," + rank);
                    send(msg);
                }
            });
        } else {
            System.out.println("Invalid arguments for agent " + getLocalName());
        }

        addBehaviour(new HandleBout());
    }

    private class HandleBout extends CyclicBehaviour {
        private boolean registered = false;

        public void action() {
            ACLMessage msg = receive();
            if (msg != null) {
                String conversationId = msg.getConversationId();

                // Registration confirm
                if (!registered && msg.getPerformative() == ACLMessage.CONFIRM) {
                    registered = true;
                }
                
                //bout information: receive referee message about bout
                if (registered && msg.getPerformative() == ACLMessage.INFORM && "bout".equals(conversationId)) {
                    // answer to referee with ability data
                    ACLMessage reply = msg.createReply();
                    reply.setPerformative(ACLMessage.INFORM);
                    reply.setConversationId("ability");
                    reply.setContent(name + "," + experience + "," + speed + "," + parry + "," + stopTrust + "," + feint + "," + emotion);
                    send(reply);
                }

                //elimination: partecipate or not at next step
                if (registered && msg.getPerformative() == ACLMessage.INFORM && "elimination".equals(conversationId)) {
                    if ("ELIMINATED".equals(msg.getContent())) {
                        //System.out.println("Turn off the agent for rank elimination: " + name + "(" + getLocalName() + ")");
                        doDelete();
                    }
                }
            } else {
                block();
            }
        }
    }
}
