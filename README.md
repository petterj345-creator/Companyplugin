# Companies — Minecraft Plugin

A Paper 1.21.x plugin that adds player-run companies with levels, licenses, rotating delivery jobs, hiring, salaries, and an admin panel.

## Features

- **Found a company** for a configurable fee — pick any name (alphanumeric).
- **Company bank** with deposits and taxed withdrawals.
- **Earnings tax tick** — every 3 hours (configurable), the server takes a configurable % of the company's **earnings since the last tick** (completed job rewards only). Deposits and existing balance aren't taxed again. Window counter resets after each tick.
- **Auto-disband** if balance drops below a minimum (default `-10000`).
- **Auto-payout to contributors** — every ~20 minutes (1 Minecraft day, configurable), every member who completed jobs gets a configurable percentage of their queued earnings paid directly to their personal Vault balance. The owner sets the rate (min 10%, max 100%, default 10%). If the company can't afford the payout this cycle, it's skipped and the queue persists until next time. Fired workers are paid out their full pending balance immediately.
- **Tax + payout countdowns** — hover the main info icon to see how long until the next daily tax tick and next payout.
- **Company levels** — spend company funds to level up. Each level grants a small reward multiplier on all jobs. Cost grows exponentially with level. Current level appears on the main info icon hover.
- **License system** — admins define license types via an in-game GUI. Each license type can have **multiple job templates**; the license rotates between them, giving variety. Companies buy licenses with company funds.
- **Rotating jobs** — every active license picks one of its templates at random every X minutes (default 180 = 3h) and turns it into a randomized delivery job. Expired licenses get pruned automatically. **Completed or expired jobs are also re-rolled instantly when a player opens the Jobs GUI**, so you never have to wait for the next rotation just because you finished early.
- **In-GUI delivery** — drop items into slots inside the jobs GUI to fulfil active jobs.
- **Worker hiring** — companies toggle themselves "hiring", players see them on the public job board (`/company jobs`), apply, and the owner/manager accepts in-GUI.
- **Salary button** — owner/manager left-click a worker's head and enter an amount in chat.
- **Roles** — Owner, Manager, Worker. Owners can disband; managers can hire/buy licenses/withdraw/upgrade; workers can deposit and deliver.
- **Contribution tracker** — every deposit and completed job adds to the contributor's lifetime stat.
- **Admin panel** — `companies.admin` permission unlocks an icon in the main GUI and `/company admin`. Includes a **Server Settings** submenu where you can change taxes, costs, and intervals in-game; values save to `config.yml` immediately. `/company reload` re-reads the config without restarting (interval changes still need a restart to fully apply).
- **Back buttons** everywhere — every submenu has an arrow to step back up the hierarchy.

## Dependencies

- **Paper 1.21.x** (Spigot may work but Paper is what it's built against)
- **Vault** + a Vault-compatible economy plugin (EssentialsX, CMI, etc.)
- SQLite driver is shaded in

## Build

This repo builds itself on GitHub. You don't need Java or Maven installed locally.

**Get the compiled jar:**

1. Push the repo to GitHub (or fork it).
2. Open the **Actions** tab → click the latest **Build** run → scroll down to **Artifacts** → download `Companies-jar`.
3. Inside that zip is `Companies-1.0.0.jar`. Drop it in your server's `plugins/` folder.

**Release builds:** push a tag like `v1.0.0` and the workflow automatically creates a GitHub Release with the jar attached.

**Manual build (optional):** if you do want to build locally, install JDK 21 + Maven and run `mvn clean package`. Output lands in `target/`.

## Commands

| Command | Description |
| --- | --- |
| `/company` | Open your company (or the public job board if you're not in one) |
| `/company create <name>` | Found a new company |
| `/company jobs` | Open the public hiring board |
| `/company leave` | Leave your current company (non-owners) |
| `/company admin` | Open the admin license-type panel (needs `companies.admin`) |
| `/company reload` | Reload `config.yml` (needs `companies.admin`) |

## Permissions

| Node | Default | Purpose |
| --- | --- | --- |
| `companies.use` | `true` | Use everything except admin |
| `companies.admin` | `op` | Manage license types |

## Config (`config.yml`)

```yaml
economy:
  creation-cost: 25000.0
  withdraw-tax-percent: 10.0
  earnings-tax-percent: 15.0       # % of earnings each window
  earnings-tax-interval-minutes: 180  # 3h

company:
  min-balance: -10000.0
  max-workers: 15
  max-name-length: 24

levels:
  max-level: 50
  base-upgrade-cost: 50000.0  # cost level 1 -> 2
  cost-growth: 1.35           # next-level cost = previous * 1.35
  reward-multiplier-per-level: 0.05  # +5% per level

jobs:
  rotation-minutes: 180
  reward-min-multiplier: 0.9
  reward-max-multiplier: 1.2
  amount-min-multiplier: 0.8
  amount-max-multiplier: 1.2

payout:
  interval-minutes: 20      # default: every Minecraft day
  min-percent: 10.0
  max-percent: 100.0
  default-percent: 10.0
```

## How licenses work

1. An admin runs `/company admin` → **Create new license type** → picks a name, an icon material (this is how it appears in the shop), a price, and a lifetime.
2. Inside that license type, the admin opens **Job rotation** and adds one or more jobs: each has its own material, amount, reward, and duration. Example for a "Wood License":
   - Job 1: 1000 OAK_LOG → $5000 in 3h
   - Job 2: 800 SPRUCE_LOG → $5200 in 3h
   - Job 3: 1200 BIRCH_LOG → $4500 in 4h
3. The admin clicks **Save**.
4. A company owner opens the Licenses menu and buys the license type with company funds. A license is installed into the company.
5. While the license is valid, every rotation interval the license picks one of the configured jobs at random and rolls a new instance (with small randomization on the amount and reward).
6. Workers open the Jobs GUI, drop matching items into the delivery slots, click "Deliver now". On completion, the reward (multiplied by the company's level bonus) is added to the company balance and a new job rolls.
7. When the license's lifetime runs out, it's removed.

## Storage

Single SQLite file: `plugins/Companies/companies.db`. No external DB needed.
