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

@Student(author = "Philipp Reinig", matrikelnummer = 232934)
public class MyBot extends KitchenPlayerAI {
    private static final Vector IDLE = Vector.ZERO;
    HashMap<String, ActionPoint> actionPoints;
    List<ActionPoint> customers;
    List<ActionPoint> cuttingActionPoints;
    List<ActionPoint> cookingActionPoints;
    List<ActionPoint> ingredientTakingActionPoints;
    private KitchenInformation info;
    private boolean cookingOnStove = false;
    private Cooking currentCooking;

    @Override
    public void init() {
        this.actionPoints = new HashMap<>();
        this.customers = new ArrayList<>();
        this.cuttingActionPoints = new ArrayList<>();
        this.cookingActionPoints = new ArrayList<>();
        this.ingredientTakingActionPoints = new ArrayList<>();
        this.cookingOnStove = false;
        this.currentCooking = null;

    }

    @Override
    public String getName() {
        return "OwnBot";
    }

    @Override
    public void update(final KitchenInformation information, final List<Player> players) {
        this.actualUpdate(information, players);
    }

    private Cooking getCookingFromID(final long id) {
        for (final Cooking cooking : this.info.getCookings()) if (cooking.getId() == id) return cooking;
        throw new IllegalStateException("No cooking in cookings of KitchenInformation with id: " + id);
    }

    public synchronized void actualUpdate(final KitchenInformation information, final List<Player> players) {
        this.info = information;
        this.refactorActionPoints();
        System.out.println("update method called");
        final Player player = players.get(0);
        this.currentCooking = this.info.getCookings().size() > 0 ? this.info.getCookings().get(0) : null;
        if (this.currentCooking == null) {
            final ActionPoint nextCustomer = this.getNextCustomer();
            this.moveToActionPointAndUse(player, nextCustomer);
            this.currentCooking = player.getCooking();
        } else {
            final CookingStatus status = this.currentCooking.getStatus();
            System.out.println("status is: " + this.currentCooking.getStatus());
            if (status == CookingStatus.NEEDED_DISH)
                this.moveToActionPointAndUse(player, this.actionPoints.get("DISH_TAKING"));
            else if (status == CookingStatus.DISH)
                this.moveToActionPointAndUse(player, this.ingredientTakingActionPoints.get(0));
            else if (status == CookingStatus.RAW) {
                if (!MyBot.allCorrect(this.currentCooking.getIngredientsCorrect()))
                    this.moveToActionPointAndUse(player, this.ingredientTakingActionPoints.get(0));
                else if (!MyBot.allCorrect(this.currentCooking.getSpiceCorrect()))
                    this.moveToActionPointAndUse(player, this.actionPoints.get("SPICE_TAKE"));
            } else if (status == CookingStatus.READY_FOR_CUTTING) {
                this.moveToActionPointAndUse(player, this.cuttingActionPoints.get(0));
                if (player.getCooking() != null) System.out.println("updated cooking after cutting");
            } else if (status == CookingStatus.READY_FOR_COOKING) {
                this.moveToActionPointAndUse(player, this.cookingActionPoints.get(0));
                this.cookingOnStove = true;
            } else if (status == CookingStatus.COOKING) {
                player.setAction(Action.idle());
                assert (player.getAction().getMovement().equals(MyBot.IDLE));
            } else if (status == CookingStatus.SERVEABLE) {
                if (!this.cookingActionPoints.get(0).isPlayerIn(player))
                    this.moveToActionPointAndUse(player, this.cookingActionPoints.get(0));
                else if (player.getCooking() == null) {
                    assert (this.cookingActionPoints.get(0).isPlayerIn(player));
                    player.setAction(Action.use());
                    System.out.println("player took cooking");
                    System.out.println(player.getCooking());
                }
                assert (player.getCooking() != null);
                this.cookingOnStove = false;
                System.out.println(player.getAction().getOrder().name());
                if (player.getAction() != null && !player.getAction().equals(Action.idle())) ; // do nothing
                else player.setAction(Action.takeCookingUp());

                if (player.getCooking() == null) System.err.println("Cooking is null!");
                if (player.getCooking() != null) if (player.getCooking().getActionPoint() != null &&
                        player.getCooking().getActionPoint().getContent() == KitchenActionPointEnum.CUSTOMER)
                    player.setAction(Action.serve());
                else
                    this.moveToActionPointAndUse(player, this.getActionPointAt(player.getCooking().getCustomerPosition()));
            } else if (status == CookingStatus.ROTTEN)
                this.moveToActionPointAndUse(player, this.actionPoints.get("DISH_WASHING"));
            else assert (false) : "Player not doing anything! (else condition fired)";
        }

    }

    private void refactorActionPoints() {
        this.actionPoints = new HashMap<>();
        this.cookingActionPoints = new ArrayList<>();
        this.cuttingActionPoints = new ArrayList<>();
        this.ingredientTakingActionPoints = new ArrayList<>();
        this.customers = new ArrayList<>();


        for (final ActionPoint actionpoint : this.info.getActionPoints()) {
            final String name = actionpoint.getContent().name();
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

    }

    private void moveToActionPointAndUse(final Player player, final ActionPoint actionpoint) {
        if (actionpoint == null) throw new IllegalStateException("move method got actionpoint, which is null!");
        if (actionpoint.isPlayerIn(player)) player.setAction(Action.use());
        else {
            final PathResult wayFromTo = this.info.getWays().findWayFromTo(this.info, player, actionpoint.getPosition());
            player.setAction(Action.move(wayFromTo.getMovement()));
        }
        System.out.println("at end of moveToActionPointAndUse-method action of player is: " + player.getAction().getOrder().name());
    }

    private ActionPoint getActionPointAt(final Vector vector) {
        for (final ActionPoint ap : this.info.getActionPoints())
            if (ap.isInVector(vector)) return ap;
        return null;
    }

    private static boolean allCorrect(final List<Boolean> list) {
        for (final Boolean b : list) if (!b) return false;
        return true;
    }

    private ActionPoint getNextCustomer() {
        ActionPoint nextCustomer = null;
        for (final ActionPoint customer : this.customers)
            if (nextCustomer == null || customer.getWaitingTime() != -1 && customer.getWaitingTime() < nextCustomer.getWaitingTime())
                nextCustomer = customer;
        return nextCustomer;
    }
}