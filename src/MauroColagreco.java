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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

@Student(author = "Philipp Reinig", matrikelnummer = 232934)
public class MauroColagreco extends KitchenPlayerAI {
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
    LinkedList ingredientTakingActionPoints;
    List<Player> players;
    private KitchenInformation info;
    private boolean init = true;

    List<ActionPoint> goalActionPoints;
    List<PlayersCooking> currentCookings;

    List<LinkedList.Node> lastVisitedIngredientTakingActionPoints;

    @Override
    public void init() {
        this.actionPoints = new HashMap<>();
        this.customers = new ArrayList<>();
        this.cuttingActionPoints = new ArrayList<>();
        this.cookingActionPoints = new ArrayList<>();
        this.ingredientTakingActionPoints = new LinkedList();
        this.goalActionPoints = new ArrayList<>();
        this.currentCookings = new ArrayList<>();
        this.init = true;
        this.lastVisitedIngredientTakingActionPoints = new ArrayList<>();
    }

    @Override
    public String getName() {
        return "Mauro Colagreco";
    }

    @Override
    public void update(final KitchenInformation information, final List<Player> players) {
        if (this.init) {
            for (int i = 0; i < players.size(); i++) {
                this.goalActionPoints.add(null);
                this.currentCookings.add(null);
                this.lastVisitedIngredientTakingActionPoints.add(new LinkedList.Node(null, null));
            }
            this.init = false;
            if (MauroColagreco.DEBUG) {
                for (final ActionPoint ap : information.getActionPoints()) {
                    System.out.println(ap.getPosition() + ", " + ap.getContent().name() + ", " + ap.getId());
                    if (ap.getContent() == KitchenActionPointEnum.UPGRADE) {
                        final List<KitchenUpgrade> upgrades = ap.getPossibleUpgrades();
                        System.out.println("possible upgrades: " + Arrays.toString(ap.getPossibleUpgrades().toArray()));
                        for (final KitchenUpgrade upgrade : upgrades) {
                            System.out.println(upgrade.getValueString() + ": " + upgrade.getCost());
                        }
                    }
                }
            }
        }
        this.info = information;
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
                    this.moveToActionPointAndUse(player, this.actionPoints.get(MauroColagreco.DISH_TAKING), i);
                } else {
                    this.moveToActionPointAndUse(player, this.actionPoints.get(MauroColagreco.DISH_WASHING), i);
                }
            } else if (status == CookingStatus.DISH || status == CookingStatus.RAW) {
                // ================================================================================================================
                boolean needToBuy = false;
                if (MauroColagreco.hasIncorrect(playersCurrentCooking.getIngredientsCorrect())) {
                    if (this.lastVisitedIngredientTakingActionPoints.get(i) == null) {
                        if (this.ingredientTakingActionPointContainsAtLeastOneMissingIngredient((ActionPoint) this.ingredientTakingActionPoints.getFirst().getData(), playersCurrentCooking)) {
                            this.lastVisitedIngredientTakingActionPoints.set(i, this.ingredientTakingActionPoints.getFirst());
                        } else {
                            needToBuy = true;
                        }
                    } else if (this.lastVisitedIngredientTakingActionPoints.get(i) != null && this.lastVisitedIngredientTakingActionPoints.get(i).getData() == null) {
                        assert (this.ingredientTakingActionPoints.getFirst().getData() instanceof ActionPoint);
                        this.lastVisitedIngredientTakingActionPoints.set(i, this.ingredientTakingActionPoints.getFirst());
                        if (this.ingredientTakingActionPointContainsAtLeastOneMissingIngredient((ActionPoint) this.lastVisitedIngredientTakingActionPoints.get(i).getData(), playersCurrentCooking)) {
                            this.moveToActionPointAndUse(player, (ActionPoint) this.lastVisitedIngredientTakingActionPoints.get(i).getData(), i);
                        } else {
                            this.lastVisitedIngredientTakingActionPoints.set(i, this.lastVisitedIngredientTakingActionPoints.get(i).getNext());
                        }
                    } else {
                        assert (this.lastVisitedIngredientTakingActionPoints.get(i).getData() instanceof ActionPoint);
                        if (this.ingredientTakingActionPointContainsAtLeastOneMissingIngredient((ActionPoint) this.lastVisitedIngredientTakingActionPoints.get(i).getData(), playersCurrentCooking)) {
                            this.moveToActionPointAndUse(player, (ActionPoint) this.lastVisitedIngredientTakingActionPoints.get(i).getData(), i);
                        } else {
                            this.lastVisitedIngredientTakingActionPoints.set(i, this.lastVisitedIngredientTakingActionPoints.get(i).getNext());
                        }
                    }
                }
                if (MauroColagreco.hasIncorrect(playersCurrentCooking.getSpiceCorrect()) && this.spiceTakingActionPointContainsAtLeastOneMissingSpice(playersCurrentCooking)) {
                    this.moveToActionPointAndUse(player, this.actionPoints.get(MauroColagreco.SPICE_TAKE), i);
                } else if (MauroColagreco.hasIncorrect(playersCurrentCooking.getSpiceCorrect()) && !this.spiceTakingActionPointContainsAtLeastOneMissingSpice(playersCurrentCooking)) {
                    needToBuy = true;
                } else {
                    assert (!MauroColagreco.hasIncorrect(playersCurrentCooking.getSpiceCorrect()));
                }
                if (needToBuy && !this.otherPlayerOnWayToActionPointOrAlreadyThere(this.actionPoints.get(MauroColagreco.BUY), i)) {
                    this.moveToActionPointAndUse(player, this.actionPoints.get(MauroColagreco.BUY), i);
                }
                // ================================================================================================================
            } else if (status == CookingStatus.READY_FOR_CUTTING) {
                this.moveToActionPointAndUse(player, this.cuttingActionPoints.get(0), i);
            } else if (status == CookingStatus.READY_FOR_COOKING) {
                if (player.getCooking() != null) {
                    this.currentCookings.set(i, new PlayersCooking(player.getCooking(), false));
                    this.moveToActionPointAndUse(player, this.cookingActionPoints.get(0), i);
                } else {
                    if (this.getCookingActionPointWithCooking(player, this.currentCookings.get(i).getCooking(), i) != null) {
                        this.moveToActionPointAndUse(player, this.getCookingActionPointWithCooking(player, this.currentCookings.get(i).getCooking(), i), i);
                    }
                }
                this.currentCookings.get(i).setOnStove(true);
            } else if (status == CookingStatus.COOKING) {
                this.currentCookings.get(i).setOnStove(true);
                player.setAction(Action.idle());
            } else if (status == CookingStatus.SERVEABLE && player.getCooking() == null) {
                if (MauroColagreco.DEBUG) {
                }
                assert (this.currentCookings.get(i).getOnStove()) : "cooking has to be onStove at this point, but isn't";
                if (this.getCookingActionPointWithCooking(player, playersCurrentCooking, i) != null && !this.getCookingActionPointWithCooking(player, playersCurrentCooking, i).isPlayerIn(player)) {
                    this.moveToActionPointAndUse(player, this.getCookingActionPointWithCooking(player, playersCurrentCooking, i), i);
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
                this.moveToActionPointAndUse(player, this.actionPoints.get(MauroColagreco.DISH_WASHING), i);
            } else {
                assert (false) : "Player " + i + " not doing anything! (else condition fired)";
            }
        }
    }

    private ActionPoint getCookingActionPointWithCooking(final Player player, final Cooking cooking, final int i) {
        for (final ActionPoint ap : this.cookingActionPoints) {
            if (ap.getCooking() == cooking) {
                return ap;
            }
        }
        return null;
    }

    private static List<KitchenIngredient> getMissingIngredients(final Cooking cooking) {
        final List<KitchenIngredient> ingredients = cooking.getRecipe().getNeededIngredients();
        for (final KitchenIngredient ingredient : cooking.getIngredients()) {
            assert (ingredients.contains(ingredient)) : "cooking contains more ingredients than needed from recipe";
            ingredients.remove(ingredient);
        }
        return ingredients;
    }

    private boolean ingredientTakingActionPointContainsAtLeastOneMissingIngredient(final ActionPoint ingredientTakingActionPoint, final Cooking cooking) {
        final List<KitchenIngredient> availableIngredients = this.info.getSameActionPoint(ingredientTakingActionPoint).getIngredients();
        final List<KitchenIngredient> neededIngredients = MauroColagreco.getMissingIngredients(cooking);

        for (final KitchenIngredient ingredient : neededIngredients) {
            if (availableIngredients.contains(ingredient)) {
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
        final List<KitchenSpice> availableSpices = this.actionPoints.get(MauroColagreco.SPICE_TAKE).getSpices();
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
        this.ingredientTakingActionPoints = new LinkedList();
        this.customers = new ArrayList<>();

        for (final ActionPoint actionpoint : this.info.getActionPoints()) {
            final String name = actionpoint.getContent().name();
            switch (name) {
                case MauroColagreco.DISH_TAKING:
                case MauroColagreco.DISH_WASHING:
                case MauroColagreco.SPICE_TAKE:
                case MauroColagreco.BUY:
                case MauroColagreco.UPGRADE:
                    this.actionPoints.put(name, actionpoint);
                    break;
                case MauroColagreco.CUSTOMER:
                    this.customers.add(actionpoint);
                    break;
                case MauroColagreco.INGREDIENT_TAKE:
                    this.ingredientTakingActionPoints.pushback(actionpoint);
                    break;
                case MauroColagreco.CUTTING:
                    this.cuttingActionPoints.add(actionpoint);
                    break;
                case MauroColagreco.COOKING:
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
            if (this.otherPlayerOnWayToActionPointOrAlreadyThere(actionpoint, i) && this.multipleActionPointsOfTypeExist(actionpoint.getContent()) && this.getNextFreeActionPointOfSameType(actionpoint) != null && !(player.getCooking() == null && this.currentCookings.get(i) != null && this.currentCookings.get(i).getOnStove())) {
                this.moveToActionPointAndUse(player, this.getNextFreeActionPointOfSameType(actionpoint), i);
            } else {
                final PathResult wayFromTo = this.info.getWays().findWayFromTo(this.info, player, actionpoint.getPosition());
                if (MauroColagreco.DEBUG) {
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
        for (final KitchenDish cleanKitchenDish : this.actionPoints.get(MauroColagreco.DISH_TAKING).getDishes()) {
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
            if ((!customer.wasVisited() && !this.otherPlayerOnWayToActionPoint(customer, i)) && (nextCustomer == null || nextCustomer.getWaitingTime() == -1 || customer.getWaitingTime() != -1 && customer.getWaitingTime() > nextCustomer.getWaitingTime())) {
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

    public static class LinkedList {
        private Node first;

        public LinkedList() {
            this.first = null;
        }

        public Node getFirst() {
            return this.first;
        }

        public void pushback(final Object element) {
            if (this.first == null) {
                this.first = new Node(element, null);
            } else {
                Node node = this.first;
                while (node.next != null) {
                    node = node.getNext();
                }
                node.setNext(new Node(element, null));
            }
        }

        private static class Node {
            Object data;
            Node next;

            Node(final Object data, final Node next) {
                this.data = data;
                this.next = next;
            }

            public Node getNext() {
                return this.next;
            }

            public Object getData() {
                return this.data;
            }

            public void setNext(final Node next) {
                this.next = next;
            }
        }
    }
}