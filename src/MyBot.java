import com.apogames.kitchenchef.ai.Cooking;
import com.apogames.kitchenchef.ai.KitchenInformation;
import com.apogames.kitchenchef.ai.KitchenPlayerAI;
import com.apogames.kitchenchef.ai.Student;
import com.apogames.kitchenchef.ai.action.Action;
import com.apogames.kitchenchef.ai.actionPoints.ActionPoint;
import com.apogames.kitchenchef.ai.player.Player;
import com.apogames.kitchenchef.game.entity.Vector;
import com.apogames.kitchenchef.game.enums.CookingStatus;
import com.apogames.kitchenchef.game.enums.KitchenDish;
import com.apogames.kitchenchef.game.enums.KitchenIngredient;
import com.apogames.kitchenchef.game.enums.KitchenSpice;
import com.apogames.kitchenchef.game.pathfinding.PathResult;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

@Student(author = "Philipp Reinig", matrikelnummer = 232934)
public class MyBot extends KitchenPlayerAI {
    private static final Vector IDLE = Vector.ZERO;
    public static final String COOKING = "COOKING";
    public static final String CUTTING = "CUTTING";
    public static final String INGREDIENT_TAKE = "INGREDIENT_TAKE";
    public static final String CUSTOMER = "CUSTOMER";
    public static final String UPGRADE = "UPGRADE";
    public static final String SPICE_TAKE = "SPICE_TAKE";
    public static final String BUY = "BUY";
    public static final String DISH_WASHING = "DISH_WASHING";
    public static final String DISH_TAKING = "DISH_TAKING";

    private static final boolean DEBUG = false;

    HashMap<String, ActionPoint> actionPoints;
    List<ActionPoint> customers;
    List<ActionPoint> cuttingActionPoints;
    List<ActionPoint> cookingActionPoints;
    List<ActionPoint> ingredientTakingActionPoints;
    List<Player> players;
    private KitchenInformation info;
    private boolean init = true;

    List<ActionPoint> goalActionPoints;
    List<PlayersCooking> currentCookings;

    @Override
    public void init() {
        this.actionPoints = new HashMap<>();
        this.customers = new ArrayList<>();
        this.cuttingActionPoints = new ArrayList<>();
        this.cookingActionPoints = new ArrayList<>();
        this.ingredientTakingActionPoints = new ArrayList<>();
        this.goalActionPoints = new ArrayList<>();
        this.currentCookings = new ArrayList<>();
        this.init = true;
    }

    public MyBot() {

    }

    @Override
    public String getName() {
        return "OwnBot";
    }

    @Override
    public void update(final KitchenInformation information, final List<Player> players) {
        if (this.init) {
            for (int i = 0; i < players.size(); i++) {
                this.goalActionPoints.add(null);
                this.currentCookings.add(null);
            }
            this.init = false;
        }
        this.info = information;
        this.refactorActionPoints();
        this.players = players;
        for (int i = 0; i < players.size(); i++) {
            this.updatePlayer(players.get(i), i);
        }
//        for (final ActionPoint customer : this.customers) {
//            System.out.println("Customer with id: " + customer.getId() + " is at " + customer.getPosition());
//            System.out.println(customer.getContent().name());
//        }

    }

    public void updatePlayer(final Player player, final int i) {
        if (MyBot.DEBUG) {
            System.out.println("updating player: " + i);
        }
        if (player.getCooking() != null && this.currentCookings.get(i) != null && this.currentCookings.get(i).getCooking() != null && player.getCooking() != this.currentCookings.get(i).getCooking()) {
            this.currentCookings.get(i).setCooking(player.getCooking());
        }

        // Not been to customer to get order -> next action: if customer without order exists go to customer and get order, else idle
        if (player.getCooking() == null && this.currentCookings.get(i) == null) {
            final ActionPoint nextCustomer = this.getNextCustomer(i);
            //Move player to the next customer to get the order
            if (nextCustomer != null) {
                this.moveToActionPointAndUse(player, nextCustomer, i);
            }
            //No customer without order exists or other player is already on his way there / already there -> idle
            else {
                player.setAction(Action.idle());
            }
        }
        //player just got Cooking from customer, but its not yet added to currentCookings-list -> add to list now
        //status of player's cooking has to be CookingStatus.NEEDED_DISH
        else if (player.getCooking() != null && this.currentCookings.get(i) == null) {
            assert (player.getCooking().getStatus() == CookingStatus.NEEDED_DISH);
            this.currentCookings.set(i, new PlayersCooking(player.getCooking(), false));
        }
        // player has served meal that is currently in currentCookings-list -> update by setting PlayersCooking in currentCookings-list to null
        else if (player.getCooking() == null && this.currentCookings.get(i) != null && !this.currentCookings.get(i).getOnStove()) {
            this.currentCookings.set(i, null);
        } else if (player.getCooking() != this.currentCookings.get(i).getCooking() && this.currentCookings.get(i).getOnStove()) {
            assert (player.getCooking() == null);
        }
        //everything ready to run workflow update
        else {
            assert (player.getCooking() == this.currentCookings.get(i).getCooking() || (player.getCooking() == null && this.currentCookings.get(i) != null && this.currentCookings.get(i).getOnStove()));
            assert (this.currentCookings.get(i).getCooking() != null);
            if (MyBot.DEBUG) {
                System.out.println("cooking of player " + i + " is currently on stove: " + this.currentCookings.get(i).getOnStove());
            }
            final Cooking playersCurrentCooking = this.currentCookings.get(i).getCooking();
            final CookingStatus status = playersCurrentCooking.getStatus();
//            System.out.println("status is: " + playersCurrentCooking.getStatus());
            if (status == CookingStatus.NEEDED_DISH) {
                assert (player.getCooking() != null);
                if (MyBot.DEBUG) {
                    System.out.println("currently clean dishes are: " + Arrays.toString(this.actionPoints.get(MyBot.DISH_TAKING).getDishes().toArray()));
                }
                if (MyBot.DEBUG) {
                    System.out.println("player needs following dish: " + playersCurrentCooking.getRecipe().getDish());
                }
                if (this.dishClean(player.getCooking().getRecipe().getDish())) {
                    this.moveToActionPointAndUse(player, this.actionPoints.get(MyBot.DISH_TAKING), i);
                } else {
                    this.moveToActionPointAndUse(player, this.actionPoints.get(MyBot.DISH_WASHING), i);
                }
            } else if (status == CookingStatus.DISH || status == CookingStatus.RAW) {
                if (this.allIngredientsAndSpicesAvailable(player.getCooking())) {
                    if (!MyBot.allCorrect(playersCurrentCooking.getIngredientsCorrect())) {
                        this.moveToActionPointAndUse(player, this.ingredientTakingActionPoints.get(0), i);
                    } else if (!MyBot.allCorrect(playersCurrentCooking.getSpiceCorrect())) {
                        this.moveToActionPointAndUse(player, this.actionPoints.get(MyBot.SPICE_TAKE), i);
                    } else {
                        throw new IllegalStateException();
                    }
                } else {
                    this.moveToActionPointAndUse(player, this.actionPoints.get(MyBot.BUY), i);
                }
            } else if (status == CookingStatus.READY_FOR_CUTTING) {
                this.moveToActionPointAndUse(player, this.cuttingActionPoints.get(0), i);
                if (player.getCooking() != null) {
                    if (MyBot.DEBUG) {
                        System.out.println("updated cooking after cutting");
                    }
                }
            } else if (status == CookingStatus.READY_FOR_COOKING) {
                this.moveToActionPointAndUse(player, this.cookingActionPoints.get(0), i);
                if (player.getCooking() == null) {
                    this.currentCookings.get(i).setOnStove(true);
                }
            } else if (status == CookingStatus.COOKING) {
                this.currentCookings.get(i).setOnStove(true);
                player.setAction(Action.idle());
            } else if (status == CookingStatus.SERVEABLE && player.getCooking() == null) {
                if (MyBot.DEBUG) {
                    System.err.println("Cooking is currently on stove, but ready to be picked up by player " + i);
                }
                assert (this.currentCookings.get(i).getOnStove()) : "cooking has to be onStove at this point, but isn't";
                if (!this.cookingActionPoints.get(0).isPlayerIn(player)) {
                    this.moveToActionPointAndUse(player, this.cookingActionPoints.get(0), i);
                } else {
                    player.setAction(Action.use());
                }
                if (player.getCooking() != null) {
                    this.currentCookings.get(i).setOnStove(false);
                }
                if (MyBot.DEBUG) {
                    System.out.println(player.getCooking());
                }
            } else if (status == CookingStatus.SERVEABLE && player.getCooking() != null) {
                if (MyBot.DEBUG) {
                    System.err.println("Cooking was picked up by player " + i);
                }
                this.moveToActionPointAndUse(player, this.getActionPointAt(player.getCooking().getCustomerPosition()), i);
                assert (player.getCooking() != null);
                this.currentCookings.get(i).setOnStove(false);
            } else if (status == CookingStatus.ROTTEN) {
                this.moveToActionPointAndUse(player, this.actionPoints.get(MyBot.DISH_WASHING), i);
            } else {
                assert (false) : "Player " + i + " not doing anything! (else condition fired)";
            }
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
                case MyBot.DISH_TAKING:
                case MyBot.DISH_WASHING:
                case MyBot.SPICE_TAKE:
                case MyBot.BUY:
                case MyBot.UPGRADE:
                    this.actionPoints.put(name, actionpoint);
                    break;
                case MyBot.CUSTOMER:
                    this.customers.add(actionpoint);
                    break;
                case MyBot.INGREDIENT_TAKE:
                    this.ingredientTakingActionPoints.add(actionpoint);
                    break;
                case MyBot.CUTTING:
                    this.cuttingActionPoints.add(actionpoint);
                    break;
                case MyBot.COOKING:
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

    private void moveToActionPointAndUse(final Player player, final ActionPoint actionpoint, final int i) {
        if (actionpoint == null) {
            throw new IllegalStateException("move method got actionpoint, which is null!");
        }
        if (actionpoint.isPlayerIn(player)) {
            player.setAction(Action.use());
        } else {
            if (this.otherPlayerOnWayToActionPointOrAlreadyThere(actionpoint, i)) {
                player.setAction(Action.idle());
            }
            final PathResult wayFromTo = this.info.getWays().findWayFromTo(this.info, player, actionpoint.getPosition());
            if (MyBot.DEBUG) {
                System.out.println("Action of player " + i + " is: " + player.getAction().getOrder().name());
            }
            player.setAction(Action.move(wayFromTo.getMovement()));
            this.goalActionPoints.set(i, actionpoint);
        }
    }

    private ActionPoint getActionPointAt(final Vector vector) {
        for (final ActionPoint ap : this.info.getActionPoints()) {
            if (ap.isInVector(vector)) {
                return ap;
            }
        }
        return null;
    }

    private static boolean allCorrect(final List<Boolean> list) {
        for (final Boolean b : list) {
            if (!b) {
                return false;
            }
        }
        return true;
    }

    private boolean dishClean(final KitchenDish kitchenDishNeeded) {
        for (final KitchenDish cleanKitchenDish : this.actionPoints.get(MyBot.DISH_TAKING).getDishes()) {
            if (kitchenDishNeeded == cleanKitchenDish) {
                return true;
            }
        }
        return false;
    }

    private static HashMap<KitchenIngredient, Integer> listOfIngredientsToHashMap(final List<KitchenIngredient> list) {
        final HashMap<KitchenIngredient, Integer> ingredientsHashMap = new HashMap<>();
        for (final KitchenIngredient element : list) {
            if (ingredientsHashMap.containsKey(element)) {
                ingredientsHashMap.put(element, ingredientsHashMap.get(element) + 1);
            } else {
                ingredientsHashMap.put(element, 0);
            }
        }
        return ingredientsHashMap;
    }

    private static HashMap<KitchenSpice, Integer> listOfSpicesToHashMap(final List<KitchenSpice> list) {
        final HashMap<KitchenSpice, Integer> spicesHashMap = new HashMap<>();
        for (final KitchenSpice element : list) {
            if (spicesHashMap.containsKey(element)) {
                spicesHashMap.put(element, spicesHashMap.get(element) + 1);
            } else {
                spicesHashMap.put(element, 0);
            }
        }
        return spicesHashMap;
    }

    private boolean allIngredientsAndSpicesAvailable(final Cooking cooking) {
        assert (cooking != null) : "allIngredientsAndSpicesAvailable method got cooking which is null";
        final HashMap<KitchenIngredient, Integer> neededIngredients = MyBot.listOfIngredientsToHashMap(cooking.getRecipe().getNeededIngredients());
        final HashMap<KitchenSpice, Integer> neededSpices = MyBot.listOfSpicesToHashMap(cooking.getRecipe().getNeededSpice());
        final HashMap<KitchenIngredient, Integer> availableIngredients = MyBot.listOfIngredientsToHashMap(this.ingredientTakingActionPoints.get(0).getIngredients());
        final HashMap<KitchenSpice, Integer> availableSpices = MyBot.listOfSpicesToHashMap(this.actionPoints.get(MyBot.SPICE_TAKE).getSpices());

        for (final KitchenIngredient element : neededIngredients.keySet()) {
            if (availableIngredients.get(element) == null || neededIngredients.get(element) > availableIngredients.get(element)) {
                return false;
            }
        }

        for (final KitchenSpice element : neededSpices.keySet()) {
            if (availableSpices.get(element) == null || neededSpices.get(element) > availableSpices.get(element)) {
                return false;
            }
        }
        return true;
    }

    private boolean otherPlayerOnWayToActionPointOrAlreadyThere(final ActionPoint actionPoint, final int x) {
        for (int i = 0; i < this.players.size(); i++) {
            if (i == x) {
                continue;
            }
            if (actionPoint.equals(this.goalActionPoints.get(i)) /*|| actionPoint.isUsedAtTheMoment()*/) {
                return true;
            }
        }
        return false;
    }

    private ActionPoint getNextCustomer(final int i) {
        ActionPoint nextCustomer = null;
        for (final ActionPoint customer : this.customers) {
            if ((!customer.wasVisited() && !this.otherPlayerOnWayToActionPointOrAlreadyThere(customer, i)) && (nextCustomer == null || nextCustomer.getWaitingTime() == -1 || customer.getWaitingTime() != -1 && customer.getWaitingTime() > nextCustomer.getWaitingTime())) {
                nextCustomer = customer;
            }
        }
        if (nextCustomer == null) {
            System.err.println("returning nextCustomer == null");
        }
        return nextCustomer;
    }

    public static class PlayersCooking {
        private Cooking cooking;
        private boolean onStove;

        public PlayersCooking() {

        }

        public PlayersCooking(final Cooking cooking, final boolean onStove) {
            this.cooking = cooking;
            this.onStove = onStove;
        }

        public Cooking getCooking() {
            return this.cooking;
        }

        public boolean getOnStove() {
            return this.onStove;
        }

        public void setOnStove(final boolean onStove) {
            this.onStove = onStove;
        }

        public void setCooking(final Cooking cooking) {
            assert (this.cooking.getId() == cooking.getId()) : "PlayersCooking.setCooking: new cooking doesnt have the same id";
            this.cooking = cooking;
        }
    }
}