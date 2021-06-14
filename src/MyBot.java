import com.apogames.kitchenchef.ai.Cooking;
import com.apogames.kitchenchef.ai.KitchenInformation;
import com.apogames.kitchenchef.ai.KitchenPlayerAI;
import com.apogames.kitchenchef.ai.Student;
import com.apogames.kitchenchef.ai.action.Action;
import com.apogames.kitchenchef.ai.actionPoints.ActionPoint;
import com.apogames.kitchenchef.ai.player.Player;
import com.apogames.kitchenchef.game.entity.Vector;
import com.apogames.kitchenchef.game.enums.CookingStatus;
import com.apogames.kitchenchef.game.enums.KitchenActionPointEnum;
import com.apogames.kitchenchef.game.pathfinding.PathResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@Student(author = "Dirk Aporius", matrikelnummer = 815)
public class MyBot extends KitchenPlayerAI {
    private static final Vector IDLE = Vector.ZERO;
    HashMap<String, ActionPoint> actionPoints;
    List<ActionPoint> customers;
    List<ActionPoint> cuttingActionPoints;
    List<ActionPoint> cookingActionPoints;
    List<ActionPoint> ingredientTakingActionPoints;
    private KitchenInformation info;
    private Cooking currentCooking;
    private boolean cookingOnStove = false;

    private final HashMap<Cooking, ActionPoint> customerOfCooking = new HashMap<>();

    @Override
    public String getName() {
        return "OwnBot";
    }

    @Override
    public void update(final KitchenInformation information, final List<Player> players) {
        this.info = information;
        this.refactorActionPoints();
        System.out.println("update method called");
        final Player player = players.get(0);
        System.out.println("initial currentCooking null: " + (this.currentCooking == null));
        if (this.currentCooking == null && !this.cookingOnStove) {
            System.out.println("getting new cooking from customer");
            final ActionPoint nextCustomer = this.getNextCustomer();
            System.out.println("debug id1");
            this.move(player, nextCustomer);
            this.customerOfCooking.put(player.getCooking(), nextCustomer);
            this.currentCooking = player.getCooking();
        } else {
            if (player.getCooking() != null) this.currentCooking = player.getCooking();
            if (player.getCooking() != this.currentCooking) System.err.println("COOKINGS NOT SYNCHRONIZED");
            final CookingStatus status = this.currentCooking.getStatus();
            System.out.println("status is: " + this.currentCooking.getStatus());
            if (status == CookingStatus.NEEDED_DISH) {
                this.printCurrentDishStatus();
                System.out.println("debug id2");
                this.move(player, this.actionPoints.get("DISH_TAKING"));
            } else if (status == CookingStatus.DISH) {
                System.out.println("debug id3");
                this.move(player, this.ingredientTakingActionPoints.get(0));
            } else if (status == CookingStatus.RAW) {
                if (!MyBot.allCorrect(this.currentCooking.getIngredientsCorrect())) {
                    System.out.println("debug id4");
                    this.move(player, this.ingredientTakingActionPoints.get(0));
                } else if (!MyBot.allCorrect(this.currentCooking.getSpiceCorrect())) {
                    System.out.println("debug id5");
                    this.move(player, this.actionPoints.get("SPICE_TAKE"));
                }
            } else if (status == CookingStatus.READY_FOR_CUTTING) {
                System.out.println("debug id6");
                this.move(player, this.cuttingActionPoints.get(0));
            } else if (status == CookingStatus.READY_FOR_COOKING) {
                System.out.println("debug id7");
                this.move(player, this.cookingActionPoints.get(0));
                System.out.println("out of move method");
                this.cookingOnStove = true;
                System.out.println("set cooking on stove to true");
            } else if (status == CookingStatus.COOKING) {
                player.setAction(Action.idle());
                assert (player.getAction().getMovement().equals(MyBot.IDLE));
            } else if (status == CookingStatus.SERVEABLE) {
                this.cookingOnStove = false;
                System.err.println("ERROR: getActionPoint(KitchenActionPointEnum.COOKING) leads to null!");
                if (!this.getActionPoint(KitchenActionPointEnum.COOKING).isPlayerIn(player)) {
                    System.out.println("debug id8");
                    this.move(player, this.cookingActionPoints.get(0));
                }
                if (player.getCooking() == null) player.setAction(Action.takeCookingUp());
                System.out.println("debug id9");
                System.out.println("map contains cooking: " + this.customerOfCooking.containsKey(player.getCooking()));
                this.move(player, this.customerOfCooking.get(player.getCooking()));
                this.currentCooking = null;
                System.out.println("Served meal to customer");
            } else if (status == CookingStatus.ROTTEN) {
                System.out.println("debug id10");
                this.move(player, this.actionPoints.get("DISH_WASHING"));
            } else assert (false) : "Player not doing anything! (else condition fired)";
        }
    }

    private void printCurrentDishStatus() {
        if (this.currentCooking != null)
            System.out.println("for current cooking need following dish: " + this.currentCooking.getDish());
        else System.out.println("current cooking null -> no dish");
    }

    private void refactorActionPoints() {
        this.actionPoints = new HashMap<>();
        this.cookingActionPoints = new ArrayList<>();
        this.cuttingActionPoints = new ArrayList<>();
        this.ingredientTakingActionPoints = new ArrayList<>();
        this.customers = new ArrayList<>();


        for (final ActionPoint actionpoint : this.info.getActionPoints()) {
            final String name = actionpoint.getContent().name();
            System.out.println("id: " + actionpoint.getId() + ", name: " + name);
            switch (name) {
                case "DISH_TAKING":
                case "DISH_WASHING":
                case "SPICE_TAKE":
                case "BUY":
                case "UPGRADE":
                    this.actionPoints.put(name, actionpoint);
                    break;
                case "CUSTOMER":
                    this.customers.add(actionpoint);
                    break;
                case "INGREDIENT_TAKE":
                    this.ingredientTakingActionPoints.add(actionpoint);
                    break;
                case "CUTTING":
                    this.cuttingActionPoints.add(actionpoint);
                    break;
                case "COOKING":
                    this.cookingActionPoints.add(actionpoint);
                    break;
                default:
                    throw new IllegalStateException(name + " not handled by refactorActionPoints() method!");
            }
        }
        /*
        single
            DISH_TAKING
            DISH_WASHING
            SPICE_TAKE
            BUY
            UPGRADE
        multiple
            CUSTOMER
            INGREDIENT_TAKE
            CUTTING
            COOKING
         */

        System.out.println(this.info == null);
        for (final ActionPoint kap : this.info.getActionPoints()) System.out.println(kap.getContent().name());
    }

    private void move(final Player player, final ActionPoint actionpoint) {
        if (actionpoint == null) throw new IllegalStateException("move method got actionpoint, which is null!");
        if (actionpoint.isPlayerIn(player)) player.setAction(Action.use());
        else {
            final PathResult wayFromTo = this.info.getWays().findWayFromTo(this.info, player, actionpoint.getPosition());
            player.setAction(Action.move(wayFromTo.getMovement()));
        }
    }

    private static boolean allCorrect(final List<Boolean> list) {
        for (final Boolean b : list) if (!b) return false;
        return true;
    }

    private ActionPoint getNextCustomer() {
        ActionPoint nextCustomer = null;
        for (final ActionPoint customer : this.customers) {
            System.out.println(customer.getWaitingTime());
            if (nextCustomer == null || customer.getWaitingTime() != -1 && customer.getWaitingTime() < nextCustomer.getWaitingTime())
                nextCustomer = customer;
        }
        return nextCustomer;
    }

    private ActionPoint getActionPoint(final KitchenActionPointEnum kape) {
        for (final ActionPoint ap : this.info.getActionPoints()) if (ap.getContent() == kape) return ap;
        return null;
    }
}