/*
 * Stock Trading Platform Simulation (Java)
 * ==========================================
 * A console-based stock trading simulator built with OOP principles.
 *
 * Features:
 *   - Market data display (simulated live prices + price history)
 *   - Buy / Sell operations with cash balance checks
 *   - Portfolio tracking (holdings, average cost, P&L, value-over-time)
 *   - Transaction history
 *   - Simple text-file persistence (auto-save / auto-load, no external libs)
 *
 * Compile:  javac StockTradingPlatform.java
 * Run:      java StockTradingPlatform
 */

import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

// ============================================================================
// Stock
// ============================================================================
class Stock {
    private final String symbol;
    private final String name;
    private double price;
    private final double volatility; // max % move per tick
    private final List<Double> history = new ArrayList<>();

    public Stock(String symbol, String name, double price, double volatility) {
        this.symbol = symbol.toUpperCase();
        this.name = name;
        this.price = round2(price);
        this.volatility = volatility;
        this.history.add(this.price);
    }

    public String getSymbol() { return symbol; }
    public String getName() { return name; }
    public double getPrice() { return price; }
    public double getVolatility() { return volatility; }
    public List<Double> getHistory() { return history; }

    /** Simulate one random-walk price tick. */
    public void updatePrice(Random rng) {
        double changePct = (rng.nextDouble() * 2 - 1) * volatility; // range [-vol, +vol]
        double newPrice = Math.max(0.01, price * (1 + changePct));
        price = round2(newPrice);
        history.add(price);
        if (history.size() > 200) history.remove(0);
    }

    public double dayChangePct() {
        if (history.size() < 2) return 0.0;
        double first = history.get(0);
        return round2((price - first) / first * 100);
    }

    static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    @Override
    public String toString() {
        String arrow = dayChangePct() >= 0 ? "^" : "v";
        return String.format("%-6s %-20s $%11.2f  %s %+.2f%%",
                symbol, name, price, arrow, dayChangePct());
    }
}

// ============================================================================
// Market
// ============================================================================
class Market {
    private final Map<String, Stock> stocks = new LinkedHashMap<>();
    private final Random rng = new Random();

    public void addStock(Stock s) { stocks.put(s.getSymbol(), s); }

    public Stock getStock(String symbol) {
        return stocks.get(symbol == null ? "" : symbol.toUpperCase());
    }

    public Collection<Stock> allStocks() { return stocks.values(); }

    /** Advance every stock by one time step. */
    public void tick() {
        for (Stock s : stocks.values()) s.updatePrice(rng);
    }

    public void display() {
        System.out.println();
        System.out.println("=".repeat(58));
        System.out.println(center("MARKET DATA", 58));
        System.out.println("=".repeat(58));
        System.out.printf("%-6s %-20s %11s  CHANGE%n", "SYM", "NAME", "PRICE");
        System.out.println("-".repeat(58));
        List<Stock> sorted = new ArrayList<>(stocks.values());
        sorted.sort(Comparator.comparing(Stock::getSymbol));
        for (Stock s : sorted) System.out.println(s);
        System.out.println("=".repeat(58));
    }

    private static String center(String s, int width) {
        int pad = (width - s.length()) / 2;
        return " ".repeat(Math.max(0, pad)) + s;
    }
}

// ============================================================================
// Transaction
// ============================================================================
class Transaction {
    static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    final String txId;
    final String type;     // BUY or SELL
    final String symbol;
    final int quantity;
    final double price;
    final double total;
    final LocalDateTime timestamp;

    public Transaction(String type, String symbol, int quantity, double price) {
        this.txId = UUID.randomUUID().toString().substring(0, 8);
        this.type = type;
        this.symbol = symbol;
        this.quantity = quantity;
        this.price = price;
        this.total = Stock.round2(price * quantity);
        this.timestamp = LocalDateTime.now();
    }

    /** Reconstruct from a persisted line. */
    public Transaction(String txId, String type, String symbol, int quantity,
                        double price, double total, LocalDateTime timestamp) {
        this.txId = txId;
        this.type = type;
        this.symbol = symbol;
        this.quantity = quantity;
        this.price = price;
        this.total = total;
        this.timestamp = timestamp;
    }

    public String toLine() {
        return String.join("|", txId, type, symbol, String.valueOf(quantity),
                String.valueOf(price), String.valueOf(total), timestamp.toString());
    }

    public static Transaction fromLine(String line) {
        String[] p = line.split("\\|");
        return new Transaction(p[0], p[1], p[2], Integer.parseInt(p[3]),
                Double.parseDouble(p[4]), Double.parseDouble(p[5]), LocalDateTime.parse(p[6]));
    }

    @Override
    public String toString() {
        return String.format("[%s] %-4s %4d %-6s @ $%9.2f  = $%12.2f",
                timestamp.format(FMT), type, quantity, symbol, price, total);
    }
}

// ============================================================================
// Holding
// ============================================================================
class Holding {
    final String symbol;
    int quantity;
    double avgCost;

    public Holding(String symbol, int quantity, double avgCost) {
        this.symbol = symbol;
        this.quantity = quantity;
        this.avgCost = avgCost;
    }
}

// ============================================================================
// ValueSnapshot (for performance-over-time tracking)
// ============================================================================
class ValueSnapshot {
    final LocalDateTime timestamp;
    final double totalValue;
    final double cash;
    final double holdingsValue;

    public ValueSnapshot(LocalDateTime timestamp, double totalValue, double cash, double holdingsValue) {
        this.timestamp = timestamp;
        this.totalValue = totalValue;
        this.cash = cash;
        this.holdingsValue = holdingsValue;
    }

    public String toLine() {
        return String.join("|", timestamp.toString(), String.valueOf(totalValue),
                String.valueOf(cash), String.valueOf(holdingsValue));
    }

    public static ValueSnapshot fromLine(String line) {
        String[] p = line.split("\\|");
        return new ValueSnapshot(LocalDateTime.parse(p[0]), Double.parseDouble(p[1]),
                Double.parseDouble(p[2]), Double.parseDouble(p[3]));
    }
}

// ============================================================================
// Portfolio
// ============================================================================
class Portfolio {
    double cash;
    final Map<String, Holding> holdings = new LinkedHashMap<>();
    final List<Transaction> transactions = new ArrayList<>();
    final List<ValueSnapshot> valueHistory = new ArrayList<>();

    public Portfolio() { this(10000.0); }
    public Portfolio(double startingCash) { this.cash = startingCash; }

    public String buy(Market market, String symbol, int quantity) {
        Stock stock = market.getStock(symbol);
        if (stock == null) return "No such stock: " + symbol;
        if (quantity <= 0) return "Quantity must be positive.";

        double cost = Stock.round2(stock.getPrice() * quantity);
        if (cost > cash) {
            return String.format("Insufficient funds. Need $%,.2f, have $%,.2f.", cost, cash);
        }

        Holding h = holdings.getOrDefault(stock.getSymbol(), new Holding(stock.getSymbol(), 0, 0.0));
        int newQty = h.quantity + quantity;
        h.avgCost = Stock.round2((h.avgCost * h.quantity + cost) / newQty);
        h.quantity = newQty;
        holdings.put(stock.getSymbol(), h);

        cash = Stock.round2(cash - cost);
        transactions.add(new Transaction("BUY", stock.getSymbol(), quantity, stock.getPrice()));
        return String.format("Bought %d %s @ $%,.2f (total $%,.2f).", quantity, stock.getSymbol(), stock.getPrice(), cost);
    }

    public String sell(Market market, String symbol, int quantity) {
        Stock stock = market.getStock(symbol);
        if (stock == null) return "No such stock: " + symbol;
        Holding h = holdings.get(stock.getSymbol());
        if (h == null || h.quantity < quantity) {
            int have = (h == null) ? 0 : h.quantity;
            return String.format("Cannot sell %d %s; you only own %d.", quantity, stock.getSymbol(), have);
        }

        double proceeds = Stock.round2(stock.getPrice() * quantity);
        h.quantity -= quantity;
        if (h.quantity == 0) holdings.remove(stock.getSymbol());

        cash = Stock.round2(cash + proceeds);
        transactions.add(new Transaction("SELL", stock.getSymbol(), quantity, stock.getPrice()));
        return String.format("Sold %d %s @ $%,.2f (total $%,.2f).", quantity, stock.getSymbol(), stock.getPrice(), proceeds);
    }

    public double holdingsValue(Market market) {
        double total = 0.0;
        for (Holding h : holdings.values()) {
            Stock s = market.getStock(h.symbol);
            if (s != null) total += s.getPrice() * h.quantity;
        }
        return Stock.round2(total);
    }

    public double totalValue(Market market) {
        return Stock.round2(cash + holdingsValue(market));
    }

    public void snapshot(Market market) {
        valueHistory.add(new ValueSnapshot(LocalDateTime.now(), totalValue(market), cash, holdingsValue(market)));
        if (valueHistory.size() > 500) valueHistory.remove(0);
    }

    public void performanceReport(Market market, double startingValue) {
        double current = totalValue(market);
        double gain = Stock.round2(current - startingValue);
        double gainPct = startingValue != 0 ? Stock.round2(gain / startingValue * 100) : 0.0;

        System.out.println();
        System.out.println("=".repeat(58));
        System.out.println(center("PORTFOLIO SUMMARY", 58));
        System.out.println("=".repeat(58));
        System.out.printf("Cash balance:      $%,12.2f%n", cash);
        System.out.printf("Holdings value:    $%,12.2f%n", holdingsValue(market));
        System.out.printf("Total value:       $%,12.2f%n", current);
        System.out.printf("Overall P&L:       %s$%,.2f  (%s%.2f%%)%n",
                gain >= 0 ? "+" : "", gain, gainPct >= 0 ? "+" : "", gainPct);
        System.out.println("-".repeat(58));

        if (!holdings.isEmpty()) {
            System.out.printf("%-6s %6s %12s %12s %14s%n", "SYM", "QTY", "AVG COST", "CURRENT", "P&L");
            List<Holding> sorted = new ArrayList<>(holdings.values());
            sorted.sort(Comparator.comparing(h -> h.symbol));
            for (Holding h : sorted) {
                Stock s = market.getStock(h.symbol);
                double curPrice = s != null ? s.getPrice() : 0.0;
                double pnl = Stock.round2((curPrice - h.avgCost) * h.quantity);
                System.out.printf("%-6s %6d %12.2f %12.2f %+14.2f%n", h.symbol, h.quantity, h.avgCost, curPrice, pnl);
            }
        } else {
            System.out.println("No open positions.");
        }

        if (valueHistory.size() >= 2) {
            System.out.println("-".repeat(58));
            System.out.println("Value history (most recent snapshots):");
            int from = Math.max(0, valueHistory.size() - 5);
            for (ValueSnapshot snap : valueHistory.subList(from, valueHistory.size())) {
                System.out.printf("  %s  ->  $%,.2f%n", snap.timestamp.format(Transaction.FMT), snap.totalValue);
            }
        }
        System.out.println("=".repeat(58));
    }

    public void printTransactions() {
        System.out.println();
        System.out.println("=".repeat(58));
        System.out.println(center("TRANSACTION HISTORY", 58));
        System.out.println("=".repeat(58));
        if (transactions.isEmpty()) {
            System.out.println("No transactions yet.");
        } else {
            int from = Math.max(0, transactions.size() - 20);
            for (Transaction t : transactions.subList(from, transactions.size())) {
                System.out.println(t);
            }
        }
        System.out.println("=".repeat(58));
    }

    private static String center(String s, int width) {
        int pad = (width - s.length()) / 2;
        return " ".repeat(Math.max(0, pad)) + s;
    }
}

// ============================================================================
// User
// ============================================================================
class User {
    final String username;
    final Portfolio portfolio;

    public User(String username) { this(username, new Portfolio()); }
    public User(String username, Portfolio portfolio) {
        this.username = username;
        this.portfolio = portfolio;
    }
}

// ============================================================================
// DataStore — simple text-based persistence (no external JSON library needed)
// ============================================================================
class DataStore {
    private final String filepath;

    public DataStore(String filepath) { this.filepath = filepath; }

    public void save(Market market, Map<String, User> users) {
        try (PrintWriter out = new PrintWriter(new FileWriter(filepath))) {
            // --- Market section ---
            out.println("#MARKET");
            for (Stock s : market.allStocks()) {
                out.println(String.join("|", "STOCK", s.getSymbol(), s.getName(),
                        String.valueOf(s.getPrice()), String.valueOf(s.getVolatility())));
                StringBuilder hist = new StringBuilder("HISTORY|").append(s.getSymbol()).append("|");
                List<Double> h = s.getHistory();
                for (int i = 0; i < h.size(); i++) {
                    if (i > 0) hist.append(",");
                    hist.append(h.get(i));
                }
                out.println(hist);
            }

            // --- Users section ---
            for (User u : users.values()) {
                out.println("#USER|" + u.username);
                out.println("CASH|" + u.portfolio.cash);
                for (Holding h : u.portfolio.holdings.values()) {
                    out.println(String.join("|", "HOLDING", h.symbol,
                            String.valueOf(h.quantity), String.valueOf(h.avgCost)));
                }
                for (Transaction t : u.portfolio.transactions) {
                    out.println("TX|" + t.toLine());
                }
                for (ValueSnapshot v : u.portfolio.valueHistory) {
                    out.println("SNAP|" + v.toLine());
                }
            }
        } catch (IOException e) {
            System.out.println("Warning: failed to save data (" + e.getMessage() + ")");
        }
    }

    /** Returns {market, users} or {null, null} if no save file exists / on error. */
    public Object[] load() {
        File f = new File(filepath);
        if (!f.exists()) return new Object[]{null, null};

        Market market = new Market();
        Map<String, User> users = new LinkedHashMap<>();
        Map<String, List<Double>> pendingHistory = new HashMap<>();
        User currentUser = null;

        try (BufferedReader in = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = in.readLine()) != null) {
                if (line.isEmpty()) continue;

                if (line.startsWith("#USER|")) {
                    String name = line.substring("#USER|".length());
                    currentUser = new User(name);
                    users.put(name, currentUser);
                } else if (line.equals("#MARKET")) {
                    currentUser = null;
                } else if (line.startsWith("STOCK|")) {
                    String[] p = line.split("\\|");
                    market.addStock(new Stock(p[1], p[2], Double.parseDouble(p[3]), Double.parseDouble(p[4])));
                } else if (line.startsWith("HISTORY|")) {
                    String[] p = line.split("\\|", 3);
                    String symbol = p[1];
                    List<Double> vals = new ArrayList<>();
                    for (String v : p[2].split(",")) if (!v.isEmpty()) vals.add(Double.parseDouble(v));
                    pendingHistory.put(symbol, vals);
                } else if (currentUser != null && line.startsWith("CASH|")) {
                    currentUser.portfolio.cash = Double.parseDouble(line.substring("CASH|".length()));
                } else if (currentUser != null && line.startsWith("HOLDING|")) {
                    String[] p = line.split("\\|");
                    currentUser.portfolio.holdings.put(p[1],
                            new Holding(p[1], Integer.parseInt(p[2]), Double.parseDouble(p[3])));
                } else if (currentUser != null && line.startsWith("TX|")) {
                    currentUser.portfolio.transactions.add(Transaction.fromLine(line.substring(3)));
                } else if (currentUser != null && line.startsWith("SNAP|")) {
                    currentUser.portfolio.valueHistory.add(ValueSnapshot.fromLine(line.substring(5)));
                }
            }
        } catch (IOException e) {
            System.out.println("Warning: failed to load data (" + e.getMessage() + ")");
            return new Object[]{null, null};
        }

        // apply restored price history to each stock
        for (Stock s : market.allStocks()) {
            List<Double> hist = pendingHistory.get(s.getSymbol());
            if (hist != null && !hist.isEmpty()) {
                s.getHistory().clear();
                s.getHistory().addAll(hist);
            }
        }

        return new Object[]{market, users};
    }
}

// ============================================================================
// TradingApp — CLI entry point
// ============================================================================
public class StockTradingPlatform {
    private static final String DATA_FILE = "trading_platform_data.txt";

    private final DataStore store = new DataStore(DATA_FILE);
    private final Market market;
    private final Map<String, User> users;
    private final Scanner scanner = new Scanner(System.in);
    private User currentUser;
    private double startingValue;

    public StockTradingPlatform() {
        Object[] loaded = store.load();
        Market loadedMarket = (Market) loaded[0];
        @SuppressWarnings("unchecked")
        Map<String, User> loadedUsers = (Map<String, User>) loaded[1];

        this.market = (loadedMarket != null) ? loadedMarket : seedMarket();
        this.users = (loadedUsers != null) ? loadedUsers : new LinkedHashMap<>();
    }

    private static Market seedMarket() {
        Market m = new Market();
        Object[][] seed = {
            {"AAPL", "Apple Inc.", 195.50, 0.015},
            {"GOOG", "Alphabet Inc.", 165.20, 0.018},
            {"MSFT", "Microsoft Corp.", 425.30, 0.012},
            {"AMZN", "Amazon.com Inc.", 185.75, 0.022},
            {"TSLA", "Tesla Inc.", 245.10, 0.04},
            {"NVDA", "NVIDIA Corp.", 118.60, 0.035},
            {"NFLX", "Netflix Inc.", 685.40, 0.025},
            {"META", "Meta Platforms", 505.90, 0.02},
        };
        for (Object[] row : seed) {
            m.addStock(new Stock((String) row[0], (String) row[1], (double) row[2], (double) row[3]));
        }
        return m;
    }

    private void login() {
        System.out.print("Enter username: ");
        String name = scanner.nextLine().trim();
        if (name.isEmpty()) name = "guest";

        if (!users.containsKey(name)) {
            System.out.println("Creating new account for '" + name + "' with $10,000 starting cash.");
            users.put(name, new User(name));
        }
        currentUser = users.get(name);
        startingValue = currentUser.portfolio.totalValue(market);
        System.out.printf("Welcome, %s! Total portfolio value: $%,.2f%n", name, startingValue);
    }

    private void printMenu() {
        System.out.println("""

                --------------------------------------------
                 1. View market data
                 2. Buy stock
                 3. Sell stock
                 4. View portfolio / performance
                 5. View transaction history
                 6. Advance market (simulate next tick)
                 7. Save & exit
                --------------------------------------------""");
    }

    public void run() {
        System.out.println("Welcome to the Simulated Stock Trading Platform");
        login();

        boolean running = true;
        while (running) {
            printMenu();
            System.out.print("Choose an option (1-7): ");
            String choice = scanner.nextLine().trim();

            switch (choice) {
                case "1" -> market.display();
                case "2" -> buyFlow();
                case "3" -> sellFlow();
                case "4" -> {
                    currentUser.portfolio.snapshot(market);
                    currentUser.portfolio.performanceReport(market, startingValue);
                }
                case "5" -> currentUser.portfolio.printTransactions();
                case "6" -> {
                    market.tick();
                    currentUser.portfolio.snapshot(market);
                    System.out.println("Market advanced one tick. Prices updated.");
                }
                case "7" -> {
                    currentUser.portfolio.snapshot(market);
                    store.save(market, users);
                    System.out.println("Data saved. Goodbye!");
                    running = false;
                }
                default -> System.out.println("Invalid option, try again.");
            }
        }
    }

    private void buyFlow() {
        market.display();
        System.out.print("Symbol to buy: ");
        String symbol = scanner.nextLine().trim().toUpperCase();
        System.out.print("Quantity: ");
        int qty;
        try {
            qty = Integer.parseInt(scanner.nextLine().trim());
        } catch (NumberFormatException e) {
            System.out.println("Quantity must be a whole number.");
            return;
        }
        System.out.println(currentUser.portfolio.buy(market, symbol, qty));
    }

    private void sellFlow() {
        System.out.print("Symbol to sell: ");
        String symbol = scanner.nextLine().trim().toUpperCase();
        System.out.print("Quantity: ");
        int qty;
        try {
            qty = Integer.parseInt(scanner.nextLine().trim());
        } catch (NumberFormatException e) {
            System.out.println("Quantity must be a whole number.");
            return;
        }
        System.out.println(currentUser.portfolio.sell(market, symbol, qty));
    }

    public static void main(String[] args) {
        StockTradingPlatform app = new StockTradingPlatform();
        app.run();
    }
}
