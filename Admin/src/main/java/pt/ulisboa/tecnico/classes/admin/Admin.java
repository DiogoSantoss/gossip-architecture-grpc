package pt.ulisboa.tecnico.classes.admin;

import static java.lang.System.exit;
import static pt.ulisboa.tecnico.classes.admin.AdminCommands.*;

import java.util.Arrays;
import java.util.Scanner;

public class Admin {

  public static void main(String[] args) {

    boolean debug = Arrays.asList(args).contains("-debug");

    final Scanner scanner = new Scanner(System.in);
    final AdminFrontend frontend = new AdminFrontend(debug);

    while(true) {
      System.out.printf("%n> ");

      String line = scanner.nextLine();

      // Empty command
      if (line.trim().length() == 0) {
        System.out.println();
        continue;
      }

      String[] cmdArgs = line.split(" ");

      switch (cmdArgs[0]) {

        case ACTIVATE -> activate(frontend, cmdArgs);

        case DEACTIVATE -> deactivate(frontend, cmdArgs);

        case DUMP -> dump(frontend, cmdArgs);

        case ACTIVATEGOSSIP -> activateGossip(frontend, cmdArgs);

        case DEACTIVATEGOSSIP -> deactivateGossip(frontend, cmdArgs);

        case GOSSIP -> gossip(frontend, cmdArgs);

        case EXIT -> {
          scanner.close();
          frontend.close();
          exit(0);
        }

        default -> System.out.println("No matching command found.");
      }
    }
  }

  /**
   * Get the qualifier from the command arguments
   * @param cmdArgs The command arguments
   * @return        The qualifier
   */
  private static String getQualifier(String[] cmdArgs) {
    String qualifier = "";

    // check arguments
    if(cmdArgs.length > 2) {
      System.out.println("Too many arguments.");
    }
    else if(cmdArgs.length == 1) {
      qualifier = "P";
    }
    else if(cmdArgs[1].equals("P") || cmdArgs[1].matches("^S\\d*$")){
      qualifier = cmdArgs[1];
    }
    else{
      System.out.println("Wrong arguments.");
    }

    return qualifier;
  }

  /**
   * Call activate method from frontend
   * @param frontend The admin frontend
   * @param cmdArgs The command arguments
   */
  public static void activate(AdminFrontend frontend, String[] cmdArgs) {

    String qualifier = getQualifier(cmdArgs);

    if(qualifier.isEmpty()) { return; }

    frontend.activate(qualifier);
  }

  /**
   * Call deactivate method from frontend
   * @param frontend The admin frontend
   * @param cmdArgs The command arguments
   */
  public static void deactivate(AdminFrontend frontend, String[] cmdArgs) {

    String qualifier = getQualifier(cmdArgs);

    if(qualifier.isEmpty()) { return; }

    frontend.deactivate(qualifier);
  }

  /**
   * Call dump method from frontend
   * @param frontend The admin frontend
   * @param cmdArgs The command arguments
   */
  public static void dump(AdminFrontend frontend, String[] cmdArgs) {

    String qualifier = getQualifier(cmdArgs);

    if(qualifier.isEmpty()) { return; }

    frontend.dump(qualifier);
  }

  /**
   * Call activate gossip method from frontend
   * @param frontend The admin frontend
   * @param cmdArgs The command arguments
   */
  public static void activateGossip(AdminFrontend frontend, String[] cmdArgs) {

    String qualifier = getQualifier(cmdArgs);

    if(qualifier.isEmpty()) { return; }

    frontend.activateGossip(qualifier);
  }

  /**
   * Call deactivate gossip method from frontend
   * @param frontend The admin frontend
   * @param cmdArgs The command arguments
   */
  public static void deactivateGossip(AdminFrontend frontend, String[] cmdArgs) {

    String qualifier = getQualifier(cmdArgs);

    if(qualifier.isEmpty()) { return; }

    frontend.deactivateGossip(qualifier);
  }

  /**
   * Call gossip method from frontend
   * @param frontend The admin frontend
   * @param cmdArgs The command arguments
   */
  public static void gossip(AdminFrontend frontend, String[] cmdArgs) {

    String qualifier = getQualifier(cmdArgs);

    if(qualifier.isEmpty()) { return; }

    frontend.forceGossip(qualifier);
  }
}

