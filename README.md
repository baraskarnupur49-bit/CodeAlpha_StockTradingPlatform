

# Stock Trading Platform (Java)

A console-based stock trading simulator built with Object-Oriented Programming.
Users can view live (simulated) market data, buy and sell stocks, and track
portfolio performance over time — all backed by simple file persistence so
progress survives between runs.

## Features

- **Market data display** — live simulated prices for 8 seeded stocks, with
  per-stock change % and price history
- **Buy / Sell operations** — cash-balance and holdings checks, average cost
  basis tracking
- **Portfolio performance tracking** — cash, holdings value, total value,
  overall P&L, and a rolling history of value snapshots over time
- **Transaction history** — every buy/sell logged with timestamp, price, and total
- **Persistence** — market state and all user portfolios are saved to a plain
  text file (`trading_platform_data.txt`) and reloaded automatically on the
  next run, no external database or library required
- **Multi-user** — any number of usernames can have independent portfolios in
  the same save file

## Requirements

- JDK 17 or later (uses `switch` expressions and text blocks)

## Compile & Run

```bash
javac StockTradingPlatform.java
java StockTradingPlatform
```

## Usage

On startup you'll be asked for a username. New usernames get a fresh account
with $10,000 starting cash; existing usernames resume where they left off.

```
--------------------------------------------
 1. View market data
 2. Buy stock
 3. Sell stock
 4. View portfolio / performance
 5. View transaction history
 6. Advance market (simulate next tick)
 7. Save & exit
--------------------------------------------
```

- **1 — View market data**: lists all stocks with current price and % change
  since the market was first initialized.
- **2 — Buy stock**: enter a symbol and quantity; blocked if funds are
  insufficient.
- **3 — Sell stock**: enter a symbol and quantity; blocked if you don't own
  enough shares.
- **4 — View portfolio / performance**: shows cash, holdings value, total
  value, overall P&L, a per-position P&L breakdown, and the last 5 value
  snapshots.
- **5 — View transaction history**: the last 20 buy/sell transactions.
- **6 — Advance market**: simulates one price tick for every stock (random
  walk within each stock's volatility band) and takes a portfolio snapshot.
- **7 — Save & exit**: writes all market and user data to disk and quits.

Progress is also worth saving via option 7 before closing the terminal —
closing without saving (e.g. Ctrl+C) will still attempt an auto-save on
interrupt.

## Project Structure (classes)

| Class | Responsibility |
|---|---|
| `Stock` | Symbol, name, price, volatility, price history, tick simulation |
| `Market` | Collection of `Stock`s; advances ticks; renders the market table |
| `Holding` | Quantity + average cost basis for one symbol in a portfolio |
| `Transaction` | Immutable record of a single buy/sell event |
| `ValueSnapshot` | A timestamped total-value data point for performance tracking |
| `Portfolio` | Cash, holdings, buy/sell logic, valuation, performance report |
| `User` | Username + their `Portfolio` |
| `DataStore` | Reads/writes market + all users to a text file |
| `StockTradingPlatform` | CLI entry point tying everything together |

## Persistence Format

Data is stored in `trading_platform_data.txt` (created next to the class
files, in the working directory) using a simple pipe-delimited line format —
no external JSON library required:

```
#MARKET
STOCK|AAPL|Apple Inc.|195.50|0.015
HISTORY|AAPL|195.5,196.92,...
#USER|alice
CASH|9022.50
HOLDING|AAPL|5|195.50
TX|<id>|BUY|AAPL|5|195.50|977.50|2026-08-11T08:32:24
SNAP|2026-08-11T08:32:24|9992.05|9022.50|969.55
```

The file is human-readable and safe to inspect or delete (deleting it just
reseeds the default market and starts fresh).

## Seeded Market

| Symbol | Name | Starting Price | Volatility |
|---|---|---|---|
| AAPL | Apple Inc. | $195.50 | 1.5% |
| GOOG | Alphabet Inc. | $165.20 | 1.8% |
| MSFT | Microsoft Corp. | $425.30 | 1.2% |
| AMZN | Amazon.com Inc. | $185.75 | 2.2% |
| TSLA | Tesla Inc. | $245.10 | 4.0% |
| NVDA | NVIDIA Corp. | $118.60 | 3.5% |
| NFLX | Netflix Inc. | $685.40 | 2.5% |
| META | Meta Platforms | $505.90 | 2.0% |

Volatility is the max random % move applied per simulated tick (option 6).

## Notes / Possible Extensions

- Prices move via a simple random walk; swap in a different model (mean
  reversion, real market data feed, etc.) by changing `Stock.updatePrice()`.
- Persistence is a flat text file for zero-dependency simplicity; swapping in
  SQLite or a real JSON library would only require rewriting `DataStore`.
- No limit/stop orders — all trades execute immediately at the current price.
