package pt.ulisboa.tecnico.classes.student;

import static java.lang.System.exit;
import static pt.ulisboa.tecnico.classes.student.StudentCommands.*;

import java.util.Arrays;
import java.util.Scanner;
import java.util.stream.Collectors;

public class Student {

  public static void main(String[] args) {

    boolean debug = Arrays.asList(args).contains("-debug");

    // check arguments
    if(!validArguments(args)){
      System.err.println("Closing student.");
      return;
    }

    final Scanner scanner = new Scanner(System.in);
    final StudentFrontend frontend = new StudentFrontend(debug);

    while(true) {
      System.out.printf("%n> ");

      String line = scanner.nextLine();

      // Empty command
      if (line.trim().length() == 0) {
        System.out.println();
        continue;
      }

      String[] cmd = line.split(" ");

      switch (cmd[0]) {

        case LIST -> listClass(frontend);

        case ENROLL -> enroll(frontend, args);

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
   * Check the validity of the command-line arguments
   * @param args  The command-line arguments
   * @return      The validity of the arguments
   */
  public static boolean validArguments(String[] args) {

    if(args.length < 2) {
      System.err.println("Incorrect number of arguments.");
      return false;
    }

    // Verify student id
    String studentId = args[0];
    String[] studentIdSplit = args[0].split("aluno");

    if(!studentId.startsWith("aluno") || studentIdSplit.length != 2 || studentIdSplit[1].length() != 4) {
      System.err.println("Incorrect student id.");
      return false;
    }

    try{
      Integer.parseInt(studentIdSplit[1]);
    } catch (NumberFormatException e){
      System.err.println("Incorrect student id.");
      return false;
    }

    // Verify student name
    String name = parseStudentName(args);

    if(name.length() < 3 || name.length() > 30){
      System.err.println("Incorrect student name.");
      return false;
    }

    return true;
  }

  /**
   * Build student name from command-line arguments
   * @param args  The command-line arguments
   * @return      The student name
   */
  private static String parseStudentName(String[] args) {

    boolean debug = Arrays.asList(args).contains("-debug");
    // Concat every string from args starting at index 1 separated with space
    // If debug mode is active, don't add the last string ("-debug") to the student name
    if(debug){
      return java.util.Arrays.stream(args, 1, args.length-1)
              .collect(Collectors.joining(" "));
    } else {
      return java.util.Arrays.stream(args, 1, args.length)
              .collect(Collectors.joining(" "));
    }
  }

  /**
   * Call listClass method from frontend
   * @param frontend  The student frontend
   */
  public static void listClass(StudentFrontend frontend) {

    frontend.listClass();
  }

  /**
   * Call enroll method from frontend
   * @param frontend  The student frontend
   * @param args      The command-line arguments
   */
  public static void enroll(StudentFrontend frontend, String[] args) {

    String studentId = args[0];
    String studentName = parseStudentName(args);

    frontend.enroll(studentId, studentName);
  }
}
