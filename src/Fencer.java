import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.lang.acl.ACLMessage;

public class Fencer extends Agent {
    public String name;
    public String experience;
    public String speed;
    public String parry;
    public String stopTrust;
    public String feint;
    public String emotion;
    public String rank;

    protected void setup() {
        Object[] args = getArguments();
        if (args != null && args.length == 8) {
            name = (String) args[0];
            experience = (String) args[1];
            speed = (String) args[2];
            parry = (String) args[3];
            stopTrust = (String) args[4];
            feint = (String) args[5];
            emotion = (String) args[6];
            rank = (String) args[7];

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
