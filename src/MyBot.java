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
            if (status == CookingStatus.NEEDED_DISH) {
                System.out.println("currently clean dishes are: " + Arrays.toString(this.actionPoints.get("DISH_TAKING").getDishes().toArray()));
                System.out.println("player needs following dish: " + player.getCooking().getRecipe().getDish());
                if (this.dishClean(player.getCooking().getRecipe().getDish()))
                    this.moveToActionPointAndUse(player, this.actionPoints.get("DISH_TAKING"));
                else
                    this.moveToActionPointAndUse(player, this.actionPoints.get("DISH_WASHING"));
                System.err.println("need to clean dish now");
            } else if (status == CookingStatus.DISH || status == CookingStatus.RAW)
                if (this.allIngredientsAndSpicesAvailable(player.getCooking()))
                    if (!MyBot.allCorrect(this.currentCooking.getIngredientsCorrect()))
                        this.moveToActionPointAndUse(player, this.ingredientTakingActionPoints.get(0));
                    else if (!MyBot.allCorrect(this.currentCooking.getSpiceCorrect()))
                        this.moveToActionPointAndUse(player, this.actionPoints.get("SPICE_TAKE"));
                    else throw new IllegalStateException();
                else this.moveToActionPointAndUse(player, this.actionPoints.get(MyBot.BUY));
            else if (status == CookingStatus.READY_FOR_CUTTING) {
                this.moveToActionPointAndUse(player, this.cuttingActionPoints.get(0));
                if (player.getCooking() != null) System.out.println("updated cooking after cutting");
            } else if (status == CookingStatus.READY_FOR_COOKING) {
                this.moveToActionPointAndUse(player, this.cookingActionPoints.get(0));
                this.cookingOnStove = true;
            } else if (status == CookingStatus.COOKING) {
                player.setAction(Action.idle());
                assert (player.getAction().getMovement().equals(MyBot.IDLE));
            } else if (status == CookingStatus.SERVEABLE && player.getCooking() == null) {
                if (!this.cookingActionPoints.get(0).isPlayerIn(player))
                    this.moveToActionPointAndUse(player, this.cookingActionPoints.get(0));
                else player.setAction(Action.use());
                System.out.println(player.getCooking());
            } else if (status == CookingStatus.SERVEABLE && player.getCooking() != null)
                this.moveToActionPointAndUse(player, this.getActionPointAt(player.getCooking().getCustomerPosition()));
            else if (status == CookingStatus.ROTTEN)
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

    private boolean dishClean(final KitchenDish kitchenDishNeeded) {
        for (final KitchenDish cleanKitchenDish : this.actionPoints.get("DISH_TAKING").getDishes())
            if (kitchenDishNeeded == cleanKitchenDish) return true;
        return false;
    }

    private static HashMap<KitchenIngredient, Integer> listOfIngredientsToHashMap(final List<KitchenIngredient> list) {
        final HashMap<KitchenIngredient, Integer> ingredientsHashMap = new HashMap<>();
        for (final KitchenIngredient element : list)
            if (ingredientsHashMap.containsKey(element))
                ingredientsHashMap.put(element, ingredientsHashMap.get(element) + 1);
            else ingredientsHashMap.put(element, 0);
        return ingredientsHashMap;
    }

    private static HashMap<KitchenSpice, Integer> listOfSpicesToHashMap(final List<KitchenSpice> list) {
        final HashMap<KitchenSpice, Integer> spicesHashMap = new HashMap<>();
        for (final KitchenSpice element : list)
            if (spicesHashMap.containsKey(element))
                spicesHashMap.put(element, spicesHashMap.get(element) + 1);
            else spicesHashMap.put(element, 0);
        return spicesHashMap;
    }

    private boolean allIngredientsAndSpicesAvailable(final Cooking cooking) {
        final HashMap<KitchenIngredient, Integer> neededIngredients = MyBot.listOfIngredientsToHashMap(cooking.getRecipe().getNeededIngredients());
        final HashMap<KitchenSpice, Integer> neededSpices = MyBot.listOfSpicesToHashMap(cooking.getRecipe().getNeededSpice());
        final HashMap<KitchenIngredient, Integer> availableIngredients = MyBot.listOfIngredientsToHashMap(this.ingredientTakingActionPoints.get(0).getIngredients());
        final HashMap<KitchenSpice, Integer> availableSpices = MyBot.listOfSpicesToHashMap(this.actionPoints.get(MyBot.SPICE_TAKE).getSpices());

        for (final KitchenIngredient element : neededIngredients.keySet())
            if (availableIngredients.get(element) == null || neededIngredients.get(element) > availableIngredients.get(element))
                return false;

        for (final KitchenSpice element : neededSpices.keySet())
            if (availableSpices.get(element) == null || neededSpices.get(element) > availableSpices.get(element))
                return false;

        return true;

    }

    private ActionPoint getNextCustomer() {
        ActionPoint nextCustomer = null;
        for (final ActionPoint customer : this.customers)
            if (nextCustomer == null || nextCustomer.getWaitingTime() == -1 || customer.getWaitingTime() != -1 && customer.getWaitingTime() < nextCustomer.getWaitingTime())
                nextCustomer = customer;
        return nextCustomer;
    }
}