package net.runelite.client.plugins.microbot.kspaiofighter;

import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Answers only positively identified Stronghold of Security question/answer
 * pairs. Never picks an arbitrary option or blindly advances other dialogues.
 *
 * Questions and correct answers are matched against the live dialogue widget.
 * Unknown questions, option sets and confirmations are left to the player.
 */
final class KspAioFighterStrongholdDialogue
{
    private static final long ACTION_INTERVAL_MS = 750L;
    private static final long ANSWER_RETRY_MS = 3_500L;
    private static final int MAX_ANSWER_ATTEMPTS = 3;
    private static final long RECOGNIZED_CONTINUE_WINDOW_MS = 30_000L;

    private static final List<QuestionRule> QUESTIONS = Arrays.asList(
            new QuestionRule("password or bank pin to make you a member for free", "Don't tell them anything and click the 'Report Abuse' button."),
            new QuestionRule("free giveaway or double xp invitation via in-game chat or email", "Report the incident and do not click any links."),
            new QuestionRule("free giveaway or double xp invitation via social media or a livestream", "Report the incident and do not click any links."),
            new QuestionRule("is it safe to get someone to level your account", "No, you should never allow anyone to use your account."),
            new QuestionRule("randomly selected for a prize of 1 year of free membership", "No way! I'm reporting you to Jagex!"),
            new QuestionRule("best security step you can take to keep your registered email secure", "Set up two-factor authentication with my email provider."),
            new QuestionRule("best way to secure your account", "Two-factor authentication on your account and your registered email."),
            new QuestionRule("how do i set a bank pin", "Talk to any banker."),
            new QuestionRule("receive an email asking me to verify my identity or account details", "Delete it - it is fake!", "Delete it - it's a fake!"),
            new QuestionRule("who can i give my password to", "Nobody."),
            new QuestionRule("what do i do if my account is compromised", "Secure my device and reset my password."),
            new QuestionRule("moderator asks me for my account details", "Politely tell them no, then use the 'Report Abuse' button.", "Politely tell them no and then use the 'Report Abuse' button."),
            new QuestionRule("a player trades you some valuable items", "Decline the offer and report that player."),
            new QuestionRule("where is it safe to use my old school runescape password", "Only on the Old School RuneScape website."),
            new QuestionRule("whose responsibility is it to keep your account secure", "Me."),
            new QuestionRule("i'll give you back twice whatever you gave me", "No way! You'll just take my gold for your own! Reported!"),
            new QuestionRule("is it okay to buy an old school runescape account", "No, you should never buy an account."),
            new QuestionRule("friend asks me to for my password", "Don't give them my password."),
            new QuestionRule("search for a video online", "Report the player for phishing."),
            new QuestionRule("come immediately to a particular place on a different game world", "Nope, you're tricking me into going somewhere dangerous."),
            new QuestionRule("important characteristic of a secure password", "It's never used on other websites or accounts."),
            new QuestionRule("watching a stream by someone claiming to be jagex", "Report the stream. Real Jagex streams have a 'varified' mark.", "Report the stream as a scam. Real Jagex streams have a 'verified' mark."),
            new QuestionRule("will jagex prevent me from saying my pin in game", "No."),
            new QuestionRule("website claims that they can make me a player moderator", "Nothing, it's a fake."),
            new QuestionRule("think i have a keylogger or virus", "Virus scan my device then change my password."),
            new QuestionRule("another player messages you recommending a website", "Do not visit the website and report the player who messaged you."),
            new QuestionRule("can i leave my account logged in while i'm out of the room", "No."),
            new QuestionRule("password or bank pin to make you a player moderator", "Don't give them the information and send an 'Abuse report'.", "Don't give them the information and send an 'Abuse report'"),
            new QuestionRule("example of a good bank pin", "The birthday of a famous person or event."),
            new QuestionRule("real-life friend asks for your password so he can check your stats", "Don't give out your password to anyone. Not even close friends."),
            new QuestionRule("player starts asking you about very specific details linked to your account", "Don't share your information and report the player."),
            new QuestionRule("remove a hijacker from my account", "Use the Account Recovery system."),
            new QuestionRule("part way through the stronghold of security", "Read the text and follow the advice given.")
    );

    private long lastActionMillis;
    private long lastRecognizedMillis;
    private boolean waitingForResponse;
    private String lastQuestion = "";
    private int answerAttempts;
    private String status = "";

    String status()
    {
        return status;
    }

    void reset()
    {
        lastActionMillis = 0L;
        lastRecognizedMillis = 0L;
        waitingForResponse = false;
        lastQuestion = "";
        answerAttempts = 0;
        status = "";
    }

    /**
     * @return true if an identified security dialogue is being handled or
     * deliberately held for manual action; false for unrelated dialogues.
     */
    boolean tick()
    {
        if (!Rs2Dialogue.isInDialogue())
        {
            // One lost widget frame should not terminate an ongoing answer
            // sequence; the timeout also prevents stale continuation.
            if (System.currentTimeMillis() - lastRecognizedMillis > 2_000L)
            {
                reset();
            }
            return false;
        }

        long now = System.currentTimeMillis();
        if (Rs2Dialogue.hasSelectAnOption())
        {
            String question = Rs2Dialogue.getQuestion();
            boolean strongholdQuestion = question != null
                    && (normalize(question).contains("to pass you must answer me this")
                        || matchQuestion(question) != null);

            if (strongholdQuestion)
            {
                String signature = normalize(question);
                if (!signature.equals(lastQuestion))
                {
                    lastQuestion = signature;
                    answerAttempts = 0;
                    waitingForResponse = false;
                }
                QuestionRule rule = matchQuestion(question);
                if (rule == null)
                {
                    waitingForResponse = false;
                    status = "Unrecognized Stronghold question - manual answer required";
                    return true;
                }

                List<Widget> options = Rs2Dialogue.getDialogueOptions();
                String answer = rule.matchAnswer(options);
                if (answer == null)
                {
                    waitingForResponse = false;
                    status = "Stronghold answer not recognized - manual answer required";
                    return true;
                }

                if (answerAttempts >= MAX_ANSWER_ATTEMPTS)
                {
                    status = "Stronghold response did not advance - manual action required";
                    return true;
                }

                status = waitingForResponse
                        ? "Waiting for Stronghold answer response"
                        : "Stronghold question recognized";
                // Avoid re-clicking a question on every 100ms fighter tick.
                if ((!waitingForResponse && now - lastActionMillis >= ACTION_INTERVAL_MS)
                        || (waitingForResponse && now - lastActionMillis >= ANSWER_RETRY_MS))
                {
                    if (Rs2Dialogue.clickOption(true, answer))
                    {
                        answerAttempts++;
                        lastActionMillis = now;
                        lastRecognizedMillis = now;
                        waitingForResponse = true;
                        status = "Answering Stronghold security question";
                    }
                }
                return true;
            }

            if (waitingForResponse && now - lastRecognizedMillis < RECOGNIZED_CONTINUE_WINDOW_MS)
            {
                // The response might contain an unexpected follow-up menu.
                // Do not guess a Yes/No or "Proceed" option.
                status = "Waiting for manual Stronghold dialogue choice";
                return true;
            }

            return false;
        }

        if (waitingForResponse && now - lastRecognizedMillis < RECOGNIZED_CONTINUE_WINDOW_MS
                && Rs2Dialogue.hasContinue())
        {
            status = "Continuing recognized Stronghold answer";
            if (now - lastActionMillis >= ACTION_INTERVAL_MS)
            {
                Rs2Dialogue.clickContinue();
                lastActionMillis = now;
            }
            return true;
        }

        return false;
    }

    static String findMatchingAnswer(String question, List<String> visibleOptions)
    {
        QuestionRule rule = matchQuestion(question);
        return rule == null ? null : rule.matchStrings(visibleOptions);
    }

    private static QuestionRule matchQuestion(String question)
    {
        if (question == null)
        {
            return null;
        }
        String normalizedQuestion = normalize(question);
        for (QuestionRule rule : QUESTIONS)
        {
            if (normalizedQuestion.contains(rule.questionFragment))
            {
                return rule;
            }
        }
        return null;
    }

    private static String normalize(String text)
    {
        if (text == null)
        {
            return "";
        }
        return text.replaceAll("<[^>]*>", " ")
                .replace("&apos;", "'")
                .replace("&#39;", "'")
                .replace("&quot;", "\"")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim().replaceAll("\\s+", " ");
    }

    private static final class QuestionRule
    {
        private final String questionFragment;
        private final String[] answers;

        private QuestionRule(String questionFragment, String... answers)
        {
            this.questionFragment = normalize(questionFragment);
            this.answers = answers;
        }

        private String matchAnswer(List<Widget> options)
        {
            if (options == null)
            {
                return null;
            }
            for (Widget option : options)
            {
                if (option == null || option.getText() == null)
                {
                    continue;
                }
                String normalized = normalize(option.getText());
                for (String answer : answers)
                {
                    if (normalized.equals(normalize(answer)))
                    {
                        // Rs2Dialogue.clickOption(true, ...) must match exact
                        // rendered text; preserve the value from the widget.
                        return option.getText();
                    }
                }
            }
            return null;
        }

        private String matchStrings(List<String> options)
        {
            if (options == null)
            {
                return null;
            }
            for (String option : options)
            {
                for (String answer : answers)
                {
                    if (normalize(option).equals(normalize(answer)))
                    {
                        return option;
                    }
                }
            }
            return null;
        }
    }
}
