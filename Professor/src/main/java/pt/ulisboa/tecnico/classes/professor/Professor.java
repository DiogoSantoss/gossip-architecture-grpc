package pt.ulisboa.tecnico.classes.professor;

import static java.lang.System.exit;
import static pt.ulisboa.tecnico.classes.professor.ProfessorCommands.*;

import java.util.Arrays;
import java.util.Scanner;

public class Professor {

  public static void main(String[] args) {

    boolean debug = Arrays.asList(args).contains("-debug");

    final Scanner scanner = new Scanner(System.in);
    final ProfessorFrontend frontend = new ProfessorFrontend(debug);

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

        case OPEN_ENROLLMENTS -> openEnrollments(frontend, cmdArgs);

        case CLOSE_ENROLLMENTS -> closeEnrollments(frontend);

        case LIST -> listClass(frontend);

        case CANCEL_ENROLLMENT -> cancelEnrollment(frontend, cmdArgs);

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
   * Call openEnrollment method from frontend
   * @param frontend  The professor frontend
   * @param cmdArgs   The command arguments
   */
  public static void openEnrollments(ProfessorFrontend frontend, String[] cmdArgs) {

    // check arguments
    if(cmdArgs.length != 2) {
      System.out.println("Class capacity missing.");
      return;
    }

    int capacity;

    try{
      capacity = Integer.parseInt(cmdArgs[1]);
    } catch(NumberFormatException e){
      System.err.println("Capacity must be an integer.");
      return;
    }

    if(capacity < 0){
      System.out.println("Class capacity must be a positive integer.");
      return;
    }

    frontend.openEnrollments(capacity);
  }

  /**
   * Call closeEnrollment method from frontend
   * @param frontend  The professor frontend
   */
  public static void closeEnrollments(ProfessorFrontend frontend) {

    frontend.closeEnrollments();
  }

  /**
   * Call listClass method from frontend
   * @param frontend  The professor frontend
   */
  public static void listClass(ProfessorFrontend frontend) {

    frontend.listClass();
  }

  /**
   * Call cancelEnrollment method from frontend
   * @param frontend  The professor frontend
   * @param cmdArgs   The command arguments
   */
  public static void cancelEnrollment(ProfessorFrontend frontend, String[] cmdArgs) {

    // check arguments
    if(cmdArgs.length != 2){
      System.out.println("Student Id missing.");
      return;
    }

    frontend.cancelEnrollment(cmdArgs[1]);
  }
}
