package agents;

import java.util.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import lombok.Data;
import lombok.ToString;
import lombok.NoArgsConstructor;

import com.mindsmiths.armory.ArmoryAPI;
import com.mindsmiths.armory.components.*;
import com.mindsmiths.armory.templates.*;

import com.mindsmiths.employeeManager.employees.Employee;

import com.mindsmiths.mitems.Mitems;
import com.mindsmiths.mitems.Option;

import com.mindsmiths.ruleEngine.model.Agent;

import com.mindsmiths.pairingalgorithm.Days;
import com.mindsmiths.emailAdapter.EmailAdapterAPI;
import com.mindsmiths.emailAdapter.SendEmailPayload;

import com.mindsmiths.sdk.utils.templating.Templating;

import utils.Settings;

import models.AvaWeeklyStage;
import models.OnboardingStage;

@Data
@ToString(callSuper = true)
@NoArgsConstructor
public class Ava extends Agent {
    private List<Days> availableDays = new ArrayList<>();
    private String matchName;
    private Days matchDay;
    private Employee employee;
    private AvaWeeklyStage weeklyStage = AvaWeeklyStage.FIND_AVAILABILITY;
    private OnboardingStage onboardingStage;

    private Map<String, Employee> otherEmployees;
    private boolean workingHours;
    private Date statsEmailLastSentAt;

    public Ava(String connectionName, String connectionId) {
        super(connectionName, connectionId);
    }

    public void updateAvailableDays(List<String> availableDaysStr) {
        this.availableDays = new ArrayList<>();
        for(String day: availableDaysStr) {
            this.availableDays.add(Days.valueOf(day));
        }
    }

    public void showScreen(BaseTemplate screen) {
        ArmoryAPI.showScreen(getConnection("armory"), screen);
    }

    public void showScreens(String firstScreenId, Map<String, BaseTemplate> screens) {
        ArmoryAPI.showScreens(getConnection("armory"), firstScreenId, screens);
    }

    public void notWorkingHours() {
        BaseTemplate notWorkingScreen = new TemplateGenerator()
            .addComponent("title", new TitleComponent(Mitems.getText("weekly-core.message-about-not-working-hours-for-links.title")));
        showScreen(notWorkingScreen);
    }

    public void chooseAvailableDaysScreen() {
        Option[] days = Mitems.getOptions("weekly-core.days.each-day");
        List<CloudSelectComponent.Option> options = new ArrayList<>();
        
        for(Option option: days)
            options.add(new CloudSelectComponent.Option(option.getText(), option.getId(), true));

        BaseTemplate daysScreen = new TemplateGenerator()
            .addComponent("title", new TitleComponent(Mitems.getText("weekly-core.title-asking-for-available-days.title")))
            .addComponent("text", new DescriptionComponent(Mitems.getText("weekly-core.description-asking-for-available-days.text")))
            .addComponent("cloudSelect", new CloudSelectComponent("availableDays", options))
            .addComponent("submit", new PrimarySubmitButtonComponent("submit", "confirmDays"));
        showScreen(daysScreen);
    }

    public void showNotAvailable() {
        BaseTemplate notAvailableScreen = new TemplateGenerator()
            .addComponent("title", new TitleComponent(Mitems.getText("weekly-core.title-for-person-who-is-not-available-any-day.title")));
        // implement free form where they have to explain why they are not available
        showScreen(notAvailableScreen);
    }

    public void confirmingDaysScreen() {
        Map<String, BaseTemplate> screens = Map.of(
            "confirmDaysScreen", new TemplateGenerator("confirmScreen")
                .addComponent("title", new TitleComponent(Mitems.getText("weekly-core.confirmation-of-choosen-available-days.title")))
                .addComponent("button", new PrimarySubmitButtonComponent("submit", "confirmDaysAndThanksScreen")),
            "confirmDaysAndThanksScreen", new TemplateGenerator("confirmAndThanksScreen")
                .addComponent("title", new TitleComponent(Mitems.getText("weekly-core.stay-tuned-second-confirmation-of-available-days.title")))
        );
        showScreens("confirmDaysScreen", screens);
    }

    public void showFamiliarityQuizScreens() {
        Map<String, BaseTemplate> screens = new HashMap<String, BaseTemplate>();
        String avaImagePath = Mitems.getText("onboarding.familiarity-quiz.ava-image-path");
        List<Map<String, String>> names = createName();

        // Adding intro screen
        Option[] introButton = Mitems.getOptions("onboarding.familiarity-quiz.intro-button");
        String introScreenTitle = Mitems.getText("onboarding.familiarity-quiz.intro-screen-title");
        String introScreenDescription = Mitems.getText("onboarding.familiarity-quiz.intro-screen-description");

        screens.put("introScreen", new TemplateGenerator()
                .addComponent("image", new ImageComponent(avaImagePath))
                .addComponent("title", new TitleComponent(introScreenTitle))
                .addComponent("description", new DescriptionComponent(introScreenDescription))
                .addComponent("submit", new PrimarySubmitButtonComponent(introButton[0].getText(), "question1")));
        // Adding questions and final screen in familiarity quiz
        int questionNum = 1;
        Option[] submitButton = Mitems.getOptions("onboarding.familiarity-quiz.submit-button");

        while (true) {
            String questionTag = "question" + String.valueOf(questionNum);
            String nextQuestionTag = "question" + String.valueOf(questionNum + 1);
            String answersTag = "answers" + String.valueOf(questionNum);

            try {
                String questionText = Mitems.getText("onboarding.familiarity-quiz." + questionTag);
                screens.put(questionTag, new TemplateGenerator(questionTag)
                        .addComponent("question", new TitleComponent(questionText))
                        .addComponent(answersTag, new CloudSelectComponent(answersTag, names.get(questionNum-1)))
                        .addComponent(submitButton[0].getId(), new PrimarySubmitButtonComponent(
                                submitButton[0].getId(), submitButton[0].getText(), nextQuestionTag)));
                questionNum += 1;
            } catch (Exception e) {
                // Changing button value
                String wrongQuestionTag = "question" + String.valueOf(questionNum - 1);
                TemplateGenerator templateGenerator = (TemplateGenerator) screens.get(wrongQuestionTag);
                PrimarySubmitButtonComponent buttonComponent = (PrimarySubmitButtonComponent) templateGenerator
                        .getComponents()
                        .get(submitButton[0].getId());
                buttonComponent.setValue("finishfamiliarityquiz");

                Option[] familiarityQuizFinalButton = Mitems
                        .getOptions("onboarding.familiarity-quiz.familiarity-quiz-final-button");
                String finishFamiliarityQuizText = Mitems
                        .getText("onboarding.familiarity-quiz.finish-familiarity-quiz-text");
                screens.put("finishfamiliarityquiz", new TemplateGenerator("finishfamiliarityquiz")
                        .addComponent("image", new ImageComponent(avaImagePath))
                        .addComponent("title", new TitleComponent(finishFamiliarityQuizText))
                        .addComponent(familiarityQuizFinalButton[0].getId(), new PrimarySubmitButtonComponent(
                                familiarityQuizFinalButton[0].getId(), familiarityQuizFinalButton[0].getText(),
                                "finished-familiarity-quiz")));
                String goodbyeScreen = Mitems.getText("onboarding.familiarity-quiz.goodbye-screen");
                screens.put("finished-familiarity-quiz", new TemplateGenerator("goodbye")
                        .addComponent("title", new TitleComponent(goodbyeScreen)));
                break;
            }
        }
        showScreens("introScreen", screens);
    }

    public void showPersonalQuizScreens() {
        Map<String, BaseTemplate> screens = new HashMap<String, BaseTemplate>();
        String avaImagePath = Mitems.getText("onboarding.personal-quiz.ava-image-path");

        // Adding questions and final screens
        int questionNum = 1;
        while (true) {
            String questionTag = "question" + String.valueOf(questionNum);
            String nextQuestionTag = "question" + String.valueOf(questionNum + 1);
            String answersTag = "answers" + String.valueOf(questionNum);
            Option[] submitButton = Mitems.getOptions("onboarding.personal-quiz.submit-button");

            try {
                String text = Mitems.getText("onboarding.personal-quiz." + questionTag);
                screens.put(questionTag, new TemplateGenerator(questionTag)
                        .addComponent("question", new TitleComponent(text))
                        .addComponent(answersTag, new TextAreaComponent(answersTag, "Type your answer here", true))
                        .addComponent(submitButton[0].getId(), new PrimarySubmitButtonComponent(
                                submitButton[0].getId(), submitButton[0].getText(), nextQuestionTag)));
                questionNum += 1;
            } catch (Exception e) {
                // Changing button value
                String wrongQuestionTag = "question" + String.valueOf(questionNum - 1);
                TemplateGenerator templateGenerator = (TemplateGenerator) screens.get(wrongQuestionTag);
                PrimarySubmitButtonComponent buttonComponent = (PrimarySubmitButtonComponent) templateGenerator
                        .getComponents()
                        .get(submitButton[0].getId());
                buttonComponent.setValue("finishpersonalquiz");

                Option[] finishQuizButton = Mitems.getOptions("onboarding.personal-quiz.finish-quiz-button");
                String finishPersonalQuiz = Mitems.getText("onboarding.personal-quiz.finish-personal-quiz");

                screens.put("finishpersonalquiz", new TemplateGenerator("finishpersonalquiz")
                        .addComponent("image", new ImageComponent(avaImagePath))
                        .addComponent("title", new TitleComponent(finishPersonalQuiz))
                        .addComponent(finishQuizButton[0].getId(), new PrimarySubmitButtonComponent(
                                finishQuizButton[0].getId(), finishQuizButton[0].getText(), "finished-personal-quiz")));
                break;
            }
        }
        showScreens("question1", screens);
    }

    public void showFinalScreen() {
        String goodbyeScreen = Mitems.getText("onboarding.familiarity-quiz.goodbye-screen");
        BaseTemplate screen = new TemplateGenerator("goodbye").addComponent("title", new TitleComponent(goodbyeScreen));
        showScreen(screen);
    }

    private List<Map<String, String>> createName() {
        List<Map<String, String>> names = new ArrayList<>();
        List<Integer> employeesPerQuestionDistribution = employeesPerQuestionDistribution();
        List<Employee> employees = new ArrayList<>(otherEmployees.values());

        int startIndex = 0;
        int endIndex = 0;
        for(int len : employeesPerQuestionDistribution) {
            endIndex += len;
            Map<String, String> namesPerQuestion = new HashMap<>();

            for(Employee employee : employees.subList(startIndex, endIndex)) {
                namesPerQuestion.put(employee.getFirstName() + " " + employee.getLastName(), employee.getId());
            }
            names.add(namesPerQuestion);
            startIndex = endIndex;
        }
        return names;
    }

    public void sendWelcomeEmail(Employee employee) throws IOException {
        String subject = Mitems.getText("onboarding.welcome-email.subject");
        String description = Mitems.getText("onboarding.welcome-email.description");
        String htmlTemplate = String.join("", Files.readAllLines(Paths.get("EmailTemplate.html"), StandardCharsets.UTF_8));

        String htmlBody = Templating.recursiveRender(htmlTemplate, Map.of(
            "description", description,
            "callToAction", Mitems.getText("onboarding.welcome-email.action"),
            "firstName", employee.getFirstName(),
            "armoryUrl", String.format("%s/%s?trigger=start-onboarding", Settings.ARMORY_SITE_URL, getConnection("armory"))
        ));

        SendEmailPayload e = new SendEmailPayload();
        e.setRecipients(List.of(getConnection("email")));
        e.setSubject(subject);
        e.setHtmlText(htmlBody);
        EmailAdapterAPI.newEmail(e);
    }

    public void sendStatisticsEmail(Employee employee) throws IOException {
        String subject = Mitems.getText("statistics.statistics-email.subject");
        String description = Mitems.getText("statistics.statistics-email.description");
        String htmlTemplate = String.join("", Files.readAllLines(Paths.get("EmailTemplate.html"), StandardCharsets.UTF_8));

        String htmlBody = Templating.recursiveRender(htmlTemplate, Map.of(
            "description", description,
            "callToAction", Mitems.getText("statistics.statistics-email.action"),
            "firstName", employee.getFirstName(),
            "armoryUrl", String.format("%s/%s?trigger=show-stats", Settings.ARMORY_SITE_URL, getConnection("armory"))
        ));

        SendEmailPayload e = new SendEmailPayload();
        e.setRecipients(List.of(getConnection("email")));
        e.setSubject(subject);
        e.setHtmlText(htmlBody);
        EmailAdapterAPI.newEmail(e);
    }

    private List<Integer> employeesPerQuestionDistribution() {
        List<Integer> employeesPerQuestionDistribution = new ArrayList<Integer>();
        //int numOfOtherEmployees = otherEmployees.size();
        int numOfOtherEmployees = 52;
        int numOfQuestions = 1;
        // Determining minimal number of questions
        while (true) {
            if ( (double) numOfOtherEmployees/ (double) numOfQuestions <= 10.0) {
                break;
            }
            numOfQuestions += 1;
        }
        // Calculating number of employees per question
        double employeesPerQuestion;
        int employeesPerQuestionRounded;

        while (numOfOtherEmployees > 0) {
            employeesPerQuestion = (double) numOfOtherEmployees/ (double) numOfQuestions;

            if (employeesPerQuestion % 1 != 0) {
                employeesPerQuestionRounded = (int) (employeesPerQuestion + 1);
            }
            else {
                employeesPerQuestionRounded = (int) (employeesPerQuestion);
            }

            employeesPerQuestionDistribution.add(employeesPerQuestionRounded);
            numOfOtherEmployees = numOfOtherEmployees - employeesPerQuestionRounded;
            numOfQuestions -= 1;
        }
        return employeesPerQuestionDistribution;
    }
}