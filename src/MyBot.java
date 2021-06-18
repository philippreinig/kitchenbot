import com.apogames.kitchenchef.ai.Cooking;
import com.apogames.kitchenchef.ai.KitchenInformation;
import com.apogames.kitchenchef.ai.KitchenPlayerAI;
import com.apogames.kitchenchef.ai.Student;
import com.apogames.kitchenchef.ai.action.Action;
import com.apogames.kitchenchef.ai.actionPoints.ActionPoint;
import com.apogames.kitchenchef.ai.player.Player;
import com.apogames.kitchenchef.game.entity.Vector;
import com.apogames.kitchenchef.game.enums.*;
import com.apogames.kitchenchef.game.pathfinding.PathResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@Student(author = "Philipp Reinig", matrikelnummer = 232934)
public class MyBot extends KitchenPlayerAI {
    public static final String COOKING = "COOKING";
    public static final String CUTTING = "CUTTING";
    public static final String INGREDIENT_TAKE = "INGREDIENT_TAKE";
    public static final String CUSTOMER = "CUSTOMER";
    public static final String UPGRADE = "UPGRADE";
    public static final String SPICE_TAKE = "SPICE_TAKE";
    public static final String BUY = "BUY";
    public static final String DISH_WASHING = "DISH_WASHING";
    public static final String DISH_TAKING = "DISH_TAKING";

    private static final boolean DEBUG = true;

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

    @Override
    public String getName() {
        return "Mauro Colagreco";
    }

    @Override
    public void update(final KitchenInformation information, final List<Player> players) {
        this.info = information;
        if (this.init) {
            for (int i = 0; i < players.size(); i++) {
                this.goalActionPoints.add(null);
                this.currentCookings.add(null);
            }
            this.init = false;
        }
        this.refactorActionPoints();
        this.players = players;
        for (int i = 0; i < players.size(); i++) {
            this.updatePlayer(players.get(i), i);
        }
    }

    public void updatePlayer(final Player player, final int i) {
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

                if ((this.info.getMission().getNeededPoints() > 100000 || this.info.getMission().getNeededPoints() == -1.0) && this.info.getPoints() > 40000 && !this.otherPlayerOnWayToActionPointOrAlreadyThere(this.actionPoints.get(MyBot.UPGRADE), i)) {
                    this.moveToActionPointAndUse(player, this.actionPoints.get(MyBot.UPGRADE), i);
                } else {
                    this.moveToActionPoint(player, this.customers.get(0), i);
                }
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
            assert (this.currentCookings.get(i).getCooking().getStatus() == CookingStatus.SERVEABLE);
            this.currentCookings.set(i, null);
        }
        //everything ready to run workflow update
        else {
            final Cooking playersCurrentCooking = this.info.getSameCooking(this.currentCookings.get(i).getCooking());
            assert (playersCurrentCooking != null);
            assert (player.getCooking() == this.currentCookings.get(i).getCooking() || (player.getCooking() == null && this.currentCookings.get(i) != null && this.currentCookings.get(i).getOnStove()));
            final CookingStatus status = playersCurrentCooking.getStatus();
            if (status == CookingStatus.NEEDED_DISH) {
                assert (player.getCooking() != null);
                if (this.dishClean(playersCurrentCooking.getRecipe().getDish())) {
                    this.moveToActionPointAndUse(player, this.actionPoints.get(MyBot.DISH_TAKING), i);
                } else {
                    this.moveToActionPointAndUse(player, this.actionPoints.get(MyBot.DISH_WASHING), i);
                }
            } else if (status == CookingStatus.DISH || status == CookingStatus.RAW) {
                boolean needToBuy = false;
                // INGREDIENTS

                final ActionPoint firstIngredientTakingActionPointWhichContainsAtLeastOneNeededIngredient = this.getFirstIngredientTakingActionPointWhichContainsAtLeastOneNeededIngredient(playersCurrentCooking);
                if (firstIngredientTakingActionPointWhichContainsAtLeastOneNeededIngredient != null) {
                    this.moveToActionPointAndUse(player, firstIngredientTakingActionPointWhichContainsAtLeastOneNeededIngredient, i);
                } else if (MyBot.hasIncorrect(playersCurrentCooking.getIngredientsCorrect())) {
                    needToBuy = true;
                } else {
                    assert (!MyBot.hasIncorrect(playersCurrentCooking.getIngredientsCorrect()));
                }

                // SPICES
                if (MyBot.hasIncorrect(playersCurrentCooking.getSpiceCorrect()) && this.spiceTakingActionPointContainsAtLeastOneMissingSpice(playersCurrentCooking)) {
                    this.moveToActionPointAndUse(player, this.actionPoints.get(MyBot.SPICE_TAKE), i);
                } else if (MyBot.hasIncorrect(playersCurrentCooking.getSpiceCorrect()) && !this.spiceTakingActionPointContainsAtLeastOneMissingSpice(playersCurrentCooking)) {
                    needToBuy = true;
                } else {
                    assert (!MyBot.hasIncorrect(playersCurrentCooking.getSpiceCorrect()));
                }
                if (needToBuy && !this.otherPlayerOnWayToActionPointOrAlreadyThere(this.actionPoints.get(MyBot.BUY), i)) {
                    this.moveToActionPointAndUse(player, this.actionPoints.get(MyBot.BUY), i);
                } else if (needToBuy && this.otherPlayerOnWayToActionPointOrAlreadyThere(this.actionPoints.get(MyBot.BUY), i)) {
                    // need new ingredients / spices, but other player is already on the way / there
                }
            } else if (status == CookingStatus.READY_FOR_CUTTING) {
                this.moveToActionPointAndUse(player, this.cuttingActionPoints.get(0), i);
            } else if (status == CookingStatus.READY_FOR_COOKING) {
                if (player.getCooking() != null) {
                    this.currentCookings.set(i, new PlayersCooking(player.getCooking(), false));
                    this.moveToActionPointAndUse(player, this.cookingActionPoints.get(0), i);
                } else {
                    if (this.getCookingActionPointWithCooking(this.currentCookings.get(i).getCooking()) != null) {
                        this.moveToActionPointAndUse(player, this.getCookingActionPointWithCooking(this.currentCookings.get(i).getCooking()), i);
                    }
                }
                this.currentCookings.get(i).setOnStove(true);
            } else if (status == CookingStatus.COOKING) {
                this.currentCookings.get(i).setOnStove(true);
                player.setAction(Action.idle());
            } else if (status == CookingStatus.SERVEABLE && player.getCooking() == null) {
                assert (this.currentCookings.get(i).getOnStove()) : "cooking has to be onStove at this point, but isn't";
                if (this.getCookingActionPointWithCooking(playersCurrentCooking) != null && !this.getCookingActionPointWithCooking(playersCurrentCooking).isPlayerIn(player)) {
                    this.moveToActionPointAndUse(player, this.getCookingActionPointWithCooking(playersCurrentCooking), i);
                } else {
                    player.setAction(Action.use());
                }
                if (player.getCooking() != null) {
                    this.currentCookings.get(i).setOnStove(false);
                }
            } else if (status == CookingStatus.SERVEABLE && player.getCooking() != null) {
                final Vector customersPosition = player.getCooking().getCustomerPosition();
                this.moveToActionPointAndUse(player, this.getCustomerAtVector(customersPosition), i);
                this.currentCookings.get(i).setOnStove(false);
            } else if (status == CookingStatus.ROTTEN) {
                this.moveToActionPointAndUse(player, this.actionPoints.get(MyBot.DISH_WASHING), i);
            } else {
                assert (false) : "Player " + i + " not doing anything! (else condition fired)";
            }
        }
    }

    private ActionPoint getFirstIngredientTakingActionPointWhichContainsAtLeastOneNeededIngredient(final Cooking cooking) {
        for (final ActionPoint ap : this.ingredientTakingActionPoints) {
            if (this.ingredientTakingActionPointContainsAtLeastOneMissingIngredient(ap, this.info.getSameCooking(cooking))) {
                return ap;
            }
        }
        return null;
    }

    private ActionPoint getCookingActionPointWithCooking(final Cooking cooking) {
        for (final ActionPoint ap : this.cookingActionPoints) {
            if (ap.getCooking() == cooking) {
                return ap;
            }
        }
        return null;
    }

    private static List<KitchenIngredient> getMissingIngredients(final Cooking cooking) {
        final List<KitchenIngredient> ingredients = new ArrayList<>(cooking.getRecipe().getNeededIngredients());
        for (final KitchenIngredient ingredient : cooking.getIngredients()) {
            ingredients.remove(ingredient);
        }
        return ingredients;
    }

    private boolean ingredientTakingActionPointContainsAtLeastOneMissingIngredient(final ActionPoint ingredientTakingActionPoint, final Cooking cooking) {
        final List<KitchenIngredient> availableIngredients = this.info.getSameActionPoint(ingredientTakingActionPoint).getIngredients();
        final List<KitchenIngredient> neededIngredients = MyBot.getMissingIngredients(cooking);
        for (final KitchenIngredient ingredient : neededIngredients) {
            if (MyBot.kitchenIngredientContainedInKitchenIngredientList(availableIngredients, ingredient)) {
                return true;
            }
        }
        return false;
    }

    private static boolean kitchenIngredientContainedInKitchenIngredientList(final List<KitchenIngredient> availableIngredients, final KitchenIngredient ingredient) {
        for (final KitchenIngredient ki : availableIngredients) {
            if (ingredient == ki) {
                return true;
            }
        }
        return false;
    }

    private List<KitchenSpice> getMissingSpices(Cooking cooking) {
        cooking = this.info.getSameCooking(cooking);
        final List<KitchenSpice> neededSpices = cooking.getRecipe().getNeededSpice();
        for (final KitchenSpice spice : cooking.getSpice()) {
//            assert (spices.contains(spice)) : "cooking contains more spices than needed from recipe - failing for " + spice.name();
            neededSpices.remove(spice);
        }
        return neededSpices;
    }


    private boolean spiceTakingActionPointContainsAtLeastOneMissingSpice(final Cooking cooking) {
        final List<KitchenSpice> availableSpices = this.actionPoints.get(MyBot.SPICE_TAKE).getSpices();
        final List<KitchenSpice> neededSpices = this.getMissingSpices(cooking);
        for (final KitchenSpice spice : neededSpices) {
            if (availableSpices.contains(spice)) {
                return true;
            }
        }
        return false;
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
                    assert (false) : name + " not handled by refactorActionPoints() method!";
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

    private void moveToActionPoint(final Player player, final ActionPoint actionpoint, final int i) {
        assert (actionpoint != null) : "move method got actionpoint, which is null!";
        if (actionpoint.isPlayerIn(player)) {
            player.setAction(Action.idle());
        } else {
            final PathResult wayFromTo = this.info.getWays().findWayFromTo(this.info, player, actionpoint.getPosition());
            player.setAction(Action.move(wayFromTo.getMovement()));
            this.goalActionPoints.set(i, actionpoint);
        }
    }

    private void moveToActionPointAndUse(final Player player, final ActionPoint actionpoint, final int i) {
        assert (actionpoint != null) : "move method got actionpoint, which is null!";
        if (actionpoint.isPlayerIn(player)) {
            player.setAction(Action.use());
        } else {
            if (this.otherPlayerOnWayToActionPointOrAlreadyThere(actionpoint, i) && this.multipleActionPointsOfTypeExist(actionpoint.getContent()) && this.getNextFreeActionPointOfSameType(actionpoint) != null && !(player.getCooking() == null && this.currentCookings.get(i) != null && this.currentCookings.get(i).getOnStove()) && (actionpoint.getContent() == KitchenActionPointEnum.COOKING || actionpoint.getContent() == KitchenActionPointEnum.CUTTING)) {
                this.moveToActionPointAndUse(player, this.getNextFreeActionPointOfSameType(actionpoint), i);
            } else {
                final PathResult wayFromTo = this.info.getWays().findWayFromTo(this.info, player, actionpoint.getPosition());
                if (MyBot.DEBUG) {
                }
                player.setAction(Action.move(wayFromTo.getMovement()));
                this.goalActionPoints.set(i, actionpoint);
            }
        }
    }

    private ActionPoint getNextFreeActionPointOfSameType(final ActionPoint actionpoint) {
        for (final ActionPoint ap : this.info.getActionPoints()) {
            if (actionpoint.getContent() == ap.getContent() && ap.getId() > actionpoint.getId()) {
                return ap;
            }
        }
        return null;
    }

    private ActionPoint getCustomerAtVector(final Vector vector) {
        for (final ActionPoint ap : this.info.getActionPoints()) {
            if (ap.getContent() == KitchenActionPointEnum.CUSTOMER) {
                final Vector customerPosition = ap.getCustomerPosition();
                final double distance = Math.sqrt(Math.pow(vector.x - customerPosition.x, 2) + Math.pow(vector.y - customerPosition.y, 2));
                if (distance <= ap.getRadius()) {
                    return ap;
                }
            }
        }
        assert (false) : "getActionPointAt: no actionpoint at the given vector!";
        return null;
    }

    private boolean multipleActionPointsOfTypeExist(final KitchenActionPointEnum type) {
        int counter = 0;
        for (final ActionPoint ap : this.info.getActionPoints()) {
            if (ap.getContent() == type) {
                ++counter;
            }
        }
        assert (counter >= 1);
        return counter > 1;
    }

    private static boolean hasIncorrect(final List<Boolean> list) {
        for (final Boolean b : list) {
            if (!b) {
                return true;
            }
        }
        return false;
    }

    private boolean dishClean(final KitchenDish kitchenDishNeeded) {
        for (final KitchenDish cleanKitchenDish : this.actionPoints.get(MyBot.DISH_TAKING).getDishes()) {
            if (kitchenDishNeeded == cleanKitchenDish) {
                return true;
            }
        }
        return false;
    }

    private boolean otherPlayerCurrentlyAtActionPoint(final ActionPoint actionPoint, final int x) {
        for (int i = 0; i < this.players.size(); i++) {
            if (i == x) {
                continue;
            }
            if (actionPoint.isPlayerIn(this.players.get(i))) {
                return true;
            }
        }
        return false;
    }

    private boolean otherPlayerOnWayToActionPoint(final ActionPoint actionPoint, final int x) {
        for (int i = 0; i < this.players.size(); i++) {
            if (i == x) {
                continue;
            }
            if (actionPoint.equals(this.goalActionPoints.get(i))) {
                return true;
            }
        }
        return false;
    }

    private boolean otherPlayerOnWayToActionPointOrAlreadyThere(final ActionPoint actionPoint, final int x) {
        for (int i = 0; i < this.players.size(); i++) {
            if (i == x) {
                continue;
            }
            if (actionPoint.equals(this.goalActionPoints.get(i)) || this.otherPlayerCurrentlyAtActionPoint(actionPoint, x)) {
                return true;
            }
        }
        return false;
    }


    private ActionPoint getNextCustomer(final int i) {
        ActionPoint nextCustomer = null;
        for (final ActionPoint customer : this.customers) {
            if ((!customer.wasVisited() && !this.otherPlayerOnWayToActionPoint(customer, i)) && ((nextCustomer == null && customer.getWaitingTime() != -1) || (nextCustomer != null && customer.getWaitingTime() != -1 && customer.getWaitingTime() > nextCustomer.getWaitingTime()))) {
                nextCustomer = customer;
            }
        }
        return nextCustomer;
    }

    public static class PlayersCooking {
        private Cooking cooking;
        private boolean onStove;

        public PlayersCooking(final Cooking cooking, final boolean onStove) {
            assert (cooking != null);
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
            assert (cooking != null);
            assert (this.cooking.getId() == cooking.getId()) : "PlayersCooking.setCooking: new cooking doesnt have the same id";
            this.cooking = cooking;
        }

        @Override
        public String toString() {
            return this.cooking.getId() + ": " + this.cooking.getRecipe().getName() + ", " + this.cooking.getStatus() + ", " + this.onStove;
        }
    }
}