# Event notes

## Setup

* JetBrains IDEA
* Claude 2.1.98, Max 5x plan
* Integration with IDE: command-line style in the built-in terminal
* Stack: Java + React

## Strategy: Iterative Development

* The development was split into iterations, each is self-sufficient, vertically integrated, and provides a deliverable result
* Initial planning: vanilla Claude Code in Planning mode – six iterations defined
* Superpowers framework to implement the iterations:
  * Third-party agentic pipeline available as a plugin
  * *brainstorming* skill for high-level iteration planning
  * *writing-plans* skill for detailed implementation and testing planning
  * Implementation
  * Backend and e2e test coverage
* *No manual coding*
  * Prompting only
  * Definitions and directions at the spec level only
  * Didn't touch a single line of code. Didn't even look into any
    * Provided an initial project structure and a basic UI though

## Results

* Usage
  * Four 5-hour sessions, ~85% of the limit average
    * One session hit the limit, had to wait for the cooldown period
  * All models - 26% of the weekly limit 
  * Sonnet only - 6% of the weekly limit
* Timeline
  * 2 hr: initial decomposition and planning
  * 1 hr: agentic pipeline setup
  * 1 hr: testing framework setup
  * 3 hr: implementation and testing of iterations 1 and 2
  * 3 hr: manual testing; stabilization and finalization of iterations 1 and 2 
  * 2 hr: implementation and testing of iterations 3 and 4 
  * 3 hr: implementation and testing of iterations 5 and 6
  * 0.5 hr: summary
* AI progress since the previous take on the same task is amazing
  * The tools are strongly capable of consistently building a working application almost autonomously
  * The application remains stable in the long run and doesn't fall apart as the codebase grows
  * The result is a runnable, integrated frontend + backend + data storage application
  * The application looks appealing and working if touched briefly 
  * Though the application is still sloppy and unstable if you look at it a bit deeper 

## Observations

* In my opinion, decomposition into iterations and incremental development worked well
* Quality is not outright terrible but still low
  * An attempt to fix it turns into a slow manual process, not suitable for the format of this experiment
  * I tried this for two iterations but gave up in favor of full scope implementation
  * This felt like deliberate acceptance of the generated slop – I wouldn't have done it in production 
* Quality seems to remain stable while the codebase grows – it's a significant improvement since the previous take

### Non-Technical Observations

* Event format is better this time: 2+ days window gives a more relaxing atmosphere in a non-competitive mode
* Most of the time the AI acts autonomously, but still takes quite an amount of calendar time, even when paralleled
  * Probably a limitation of the personal subscription plan?
* The process was rather enjoyable and entertaining this time, not stressful
* Many AI interactions boil down to saying "Yes" or choosing an option. It seems like text-to-speech and voice plugins
  can boost the productivity here

### Fun Facts

* The AI stuck deeply in the TestContainers + Rancher Desktop setup, down to decompiling Java byte code.
  The fix was a relatively easy library version bump, but I had to help with this
* The AI got lost in e2e tests timing out, waited for tens of minutes while tests timed out, and wasted time. I had to help here as well
* WebSocket e2e setup and image uploads took 40–50 minutes each, but AI figured these out on its own
