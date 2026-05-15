package dk.companies.commands;

import dk.companies.Companies;
import dk.companies.economy.CompanyManager;
import dk.companies.gui.*;
import dk.companies.model.Company;
import dk.companies.util.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CompanyCommand implements CommandExecutor, TabCompleter {

    private final Companies plugin;

    public CompanyCommand(Companies plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("This command is for players only.");
            return true;
        }
        if (args.length == 0) {
            openHomeFor(p);
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "help" -> sendHelp(p);
            case "create" -> {
                if (args.length < 2) {
                    MessageUtil.send(p, "&cUsage: /company create <name>");
                    return true;
                }
                String name = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                var r = plugin.companies().create(p, name);
                switch (r) {
                    case OK -> {
                        MessageUtil.send(p, "&aCompany &6" + name + " &acreated.");
                        Company c = plugin.companies().data().getCompanyOf(p.getUniqueId());
                        if (c != null) new MainMenu(plugin, p, c).open();
                    }
                    case NAME_TAKEN -> MessageUtil.send(p, "&cThat name is already taken.");
                    case NAME_INVALID -> MessageUtil.send(p, "&cInvalid name (alphanumeric, 2-" +
                            plugin.getConfig().getInt("company.max-name-length", 24) + " chars).");
                    case ALREADY_IN_COMPANY -> MessageUtil.send(p, "&cYou are already in a company.");
                    case NOT_ENOUGH_MONEY -> MessageUtil.send(p, "&cYou cannot afford the founding cost.");
                }
            }
            case "jobs", "hire", "board" -> new JobBoardMenu(plugin, p).open();
            case "admin" -> {
                if (!p.hasPermission("companies.admin")) {
                    MessageUtil.send(p, "&cNo permission.");
                    return true;
                }
                new AdminMenu(plugin, p).open();
            }
            case "leave" -> {
                Company c = plugin.companies().data().getCompanyOf(p.getUniqueId());
                if (c == null) {
                    MessageUtil.send(p, "&cYou are not in a company.");
                    return true;
                }
                if (c.getOwner().equals(p.getUniqueId())) {
                    MessageUtil.send(p, "&cYou are the owner. Use the GUI to disband instead.");
                    return true;
                }
                plugin.companies().fire(c, p.getUniqueId());
                MessageUtil.send(p, "&7You left &6" + c.getName());
            }
            default -> sendHelp(p);
        }
        return true;
    }

    private void openHomeFor(Player p) {
        Company c = plugin.companies().data().getCompanyOf(p.getUniqueId());
        if (c != null) new MainMenu(plugin, p, c).open();
        else new JobBoardMenu(plugin, p).open();
    }

    private void sendHelp(Player p) {
        MessageUtil.send(p, "&6Company commands:");
        p.sendMessage(MessageUtil.color(" &e/company &7- open your company / job board"));
        p.sendMessage(MessageUtil.color(" &e/company create <name> &7- found a new company"));
        p.sendMessage(MessageUtil.color(" &e/company jobs &7- open the public job board"));
        p.sendMessage(MessageUtil.color(" &e/company leave &7- leave your current company"));
        if (p.hasPermission("companies.admin"))
            p.sendMessage(MessageUtil.color(" &e/company admin &7- license type admin panel"));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> out = new ArrayList<>(Arrays.asList("help", "create", "jobs", "leave"));
            if (sender.hasPermission("companies.admin")) out.add("admin");
            return out;
        }
        return List.of();
    }
}
