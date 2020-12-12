package at.fhv.dluvgo.smarthome.actuators.fridge.message;

import akka.actor.typed.ActorRef;
import at.fhv.dluvgo.smarthome.Message;
import at.fhv.dluvgo.smarthome.actuators.fridge.FridgeActor;
import at.fhv.dluvgo.smarthome.actuators.fridge.OrderProcessorActor;

public class ProductOrderedSuccessfullyMessage implements Message {
    private final FridgeActor.Product product;
    private final ActorRef<Message> originalSender;
    private final OrderProcessorActor.OrderReceipt receipt;

    public ProductOrderedSuccessfullyMessage(
        FridgeActor.Product product,
        ActorRef<Message> originalSender,
        OrderProcessorActor.OrderReceipt receipt
    ) {
        this.product = product;
        this.originalSender = originalSender;
        this.receipt = receipt;
    }

    public FridgeActor.Product getProduct() {
        return product;
    }

    public ActorRef<Message> getOriginalSender() {
        return originalSender;
    }

    public OrderProcessorActor.OrderReceipt getReceipt() {
        return this.receipt;
    }

}

