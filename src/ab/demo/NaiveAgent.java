/*****************************************************************************
 ** ANGRYBIRDS AI AGENT FRAMEWORK
 ** Copyright (c) 2014, XiaoYu (Gary) Ge, Stephen Gould, Jochen Renz
 **  Sahan Abeyasinghe,Jim Keys,  Andrew Wang, Peng Zhang
 ** All rights reserved.
**This work is licensed under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
**To view a copy of this license, visit http://www.gnu.org/licenses/
 *****************************************************************************/
package ab.demo;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import ab.demo.other.ActionRobot;
import ab.demo.other.Shot;
import ab.planner.TrajectoryPlanner;
import ab.utils.StateUtil;
import ab.vision.ABObject;
import ab.vision.GameStateExtractor.GameState;
import ab.vision.Vision;

public class NaiveAgent implements Runnable {

	private ActionRobot aRobot;
	private Random randomGenerator;
	public int currentLevel = 1;
	public static int time_limit = 12;
	private Map<Integer,Integer> scores = new LinkedHashMap<Integer,Integer>();
	TrajectoryPlanner tp;
	private int previousPigCount;
	private int shotNumber;
	private int previousShotNumber;
	private Point prevTarget;
	// a standalone implementation of the Naive Agent
	public NaiveAgent() {
		
		aRobot = new ActionRobot();
		tp = new TrajectoryPlanner();
		prevTarget = null;
		shotNumber = 0;
		randomGenerator = new Random();
		// --- go to the Poached Eggs episode level selection page ---
		ActionRobot.GoFromMainMenuToLevelSelection();

	}

	
	// run the client
	public void run() {

		aRobot.loadLevel(currentLevel);
		while (true) {
			GameState state = solve();
			if (state == GameState.WON) {
				shotNumber = 0;
				try {
					Thread.sleep(3000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				int score = StateUtil.getScore(ActionRobot.proxy);
				if(!scores.containsKey(currentLevel))
					scores.put(currentLevel, score);
				else
				{
					if(scores.get(currentLevel) < score)
						scores.put(currentLevel, score);
				}
				int totalScore = 0;
				for(Integer key: scores.keySet()){

					totalScore += scores.get(key);
					System.out.println(" Level " + key
							+ " Score: " + scores.get(key) + " ");
				}
				System.out.println("Total Score: " + totalScore);
				aRobot.loadLevel(++currentLevel);
				// make a new trajectory planner whenever a new level is entered
				tp = new TrajectoryPlanner();

				// first shot on this level, try high shot first
				shotNumber = 0;
			} else if (state == GameState.LOST) {
				shotNumber = 0;
				System.out.println("Restart");
				aRobot.restartLevel();
			} else if (state == GameState.LEVEL_SELECTION) {
				shotNumber = 0;
				System.out
				.println("Unexpected level selection page, go to the last current level : "
						+ currentLevel);
				aRobot.loadLevel(currentLevel);
			} else if (state == GameState.MAIN_MENU) {
				shotNumber = 0;
				System.out
				.println("Unexpected main menu page, go to the last current level : "
						+ currentLevel);
				ActionRobot.GoFromMainMenuToLevelSelection();
				aRobot.loadLevel(currentLevel);
			} else if (state == GameState.EPISODE_MENU) {
				shotNumber = 0;
				System.out
				.println("Unexpected episode menu page, go to the last current level : "
						+ currentLevel);
				ActionRobot.GoFromMainMenuToLevelSelection();
				aRobot.loadLevel(currentLevel);
			}

		}

	}

	private double distance(Point p1, Point p2) {
		return Math
				.sqrt((double) ((p1.x - p2.x) * (p1.x - p2.x) + (p1.y - p2.y)
						* (p1.y - p2.y)));
	}

	public GameState solve()
	{

		// capture Image
		BufferedImage screenshot = ActionRobot.doScreenShot();

		// process image
		Vision vision = new Vision(screenshot);

		// find the slingshot
		Rectangle sling = vision.findSlingshotMBR();

		// confirm the slingshot
		while (sling == null && aRobot.getState() == GameState.PLAYING) {
			System.out
			.println("No slingshot detected. Please remove pop up or zoom out");
			ActionRobot.fullyZoomOut();
			screenshot = ActionRobot.doScreenShot();
			vision = new Vision(screenshot);
			sling = vision.findSlingshotMBR();
		}
        // get all the pigs
 		List<ABObject> pigs = vision.findPigsMBR();
		List<ABObject> stones = vision.findStones();
		List<ABObject> bars = vision.findHorizontalBars();
		boolean targetIsBar = false;
		boolean targetIsStone = false;

		GameState state = aRobot.getState();
		
		if (shotNumber != previousShotNumber)
		{
			if (pigs.size() > 0 && pigs.size() == previousPigCount)
			{
				System.out.println("No Pigs eliminated -> Restart");
				aRobot.restartLevel();
			}
			
			previousPigCount = pigs.size();
		}
		

		// if there is a sling, then play, otherwise just skip.
		if (sling != null) {

			if (!pigs.isEmpty()) {

				Point releasePoint = null;
				Shot shot = new Shot();
				int dx,dy;
				{
					ABObject target = null;
					
					if (shotNumber == 0 && stones.size() == 1)
					{
						//get closest stone
						float minX = 9999999f;
						for (ABObject obj : stones)
						{
							if (obj.x < minX)
							{
								minX = obj.x;
								target = obj;
							}
						}
						
						if (target == null)
							target = stones.get(0);
						targetIsStone = true;
					}
					else if (bars.size() > 0) // pick a horizontal bar
					{
						// pick the closest pig
						ABObject closestPig = null;
						float minX = 9999999;
						float maxY = 0;
						for (ABObject p : pigs)
						{
							if (p.x < minX - p.getWidth() * 2 ||
							   (p.x < minX + p.getWidth() * 2 && p.y > maxY))
							{
								minX = p.x;
								maxY = p.y;
								closestPig = p;
							}
						}
						
						if (closestPig != null)
						{
							maxY = 0;
							for (ABObject b : bars)
							{
								// if bar below pig
								if (b.x < closestPig.x + closestPig.getWidth() && b.x + b.getWidth() > closestPig.x && b.y < closestPig.y)
								{
									if (b.y > maxY)
									{
										maxY = b.y;
										target = b;
										targetIsBar = true;
									}
								}
							}
							
							//if no bar is found search for bar below pig
							if (!targetIsBar)
							{
								// pick a bar above the pig
								float minY = 9999999;
								for (ABObject b : bars)
								{
									// if bar above pig
									if (b.x < closestPig.x + closestPig.getWidth() && b.x + b.getWidth() > closestPig.x && b.y > closestPig.y)
									{
										if (b.y < minY)
										{
											minY = b.y;
											target = b;
											targetIsBar = true;
										}
									}
								}
							}
						}	
					}
					else if (shotNumber == 0 && stones.size() > 1)
					{
						//get closest stone
						float minX = 9999999f;
						for (ABObject obj : stones)
						{
							if (obj.x < minX)
							{
								minX = obj.x;
								target = obj;
							}
						}
						
						if (target == null)
							target = stones.get(0);
						targetIsStone = true;
					}
					
					if (target == null)
						//random pick a pig if nothing selected
						target = pigs.get(randomGenerator.nextInt(pigs.size()));
						
					if (targetIsStone)
						System.out.println("Target is Stone");
					else if (targetIsBar)
						System.out.println("Target is Bar");
					else
						System.out.println("Target is Pig");
					
					Point _tpt;
					if (targetIsBar)
						_tpt = new Point(target.x, target.y + (int)(target.getHeight() / 2));
					else
						_tpt = target.getCenter();// if the target is very close to before, randomly choose a
					// point near it
					if (prevTarget != null && distance(prevTarget, _tpt) < 10) {
						double _angle = randomGenerator.nextDouble() * Math.PI * 2;
						_tpt.x = _tpt.x + (int) (Math.cos(_angle) * 10);
						_tpt.y = _tpt.y + (int) (Math.sin(_angle) * 10);
						System.out.println("Randomly changing to " + _tpt);
					}

					prevTarget = new Point(_tpt.x, _tpt.y);

					// estimate the trajectory
					double err = randomGenerator.nextDouble() * 20 + 50;
					System.out.println("Trajectory-Planer Error: " + err);
					ArrayList<Point> pts = tp.estimateLaunchPoint(sling, _tpt, err);

					// do a high shot when entering a level to find an accurate velocity
					/*if (shotNumber == 0 && pts.size() > 1) 
					{
						releasePoint = pts.get(1);
					}
					else if (pts.size() == 1)
						releasePoint = pts.get(0);
					else if (pts.size() == 2)
					{
						// randomly choose between the trajectories, with a 1 in
						// 6 chance of choosing the high one
						//if (randomGenerator.nextInt(6) == 0)
						//	releasePoint = pts.get(1);
						//else
							releasePoint = pts.get(0);
					}
					else
						if(pts.isEmpty())
						{
							System.out.println("No release point found for the target");
							System.out.println("Try a shot with 45 degree");
							releasePoint = tp.findReleasePoint(sling, Math.PI/4);
						}
						*/
						
					if (pts.size() > 0)
					{
						if (targetIsStone)
							releasePoint = pts.get(1);
						else
							releasePoint = pts.get(0);
					}
					else
					{
						System.out.println("No release point found for the target");
						System.out.println("Try a shot with 45 degree");
						releasePoint = tp.findReleasePoint(sling, Math.PI/4);
					}
					
					// Get the reference point
					Point refPoint = tp.getReferencePoint(sling);


					//Calculate the tapping time according the bird type 
					if (releasePoint != null) {
						double releaseAngle = tp.getReleaseAngle(sling,
								releasePoint);
						System.out.println("Release Point: " + releasePoint);
						System.out.println("Release Angle: "
								+ Math.toDegrees(releaseAngle));
						int tapInterval = 0;
						switch (aRobot.getBirdTypeOnSling()) 
						{

						case RedBird:
							tapInterval = 0; break;               // start of trajectory
						case YellowBird:
							tapInterval = 65 + randomGenerator.nextInt(25);break; // 65-90% of the way
						case WhiteBird:
							tapInterval =  70 + randomGenerator.nextInt(20);break; // 70-90% of the way
						case BlackBird:
							tapInterval =  70 + randomGenerator.nextInt(20);break; // 70-90% of the way
						case BlueBird:
							tapInterval =  80 + randomGenerator.nextInt(10);break; // 80-90% of the way
						default:
							tapInterval =  60;
						}

						int tapTime = tp.getTapTime(sling, releasePoint, _tpt, tapInterval);
						dx = (int)releasePoint.getX() - refPoint.x;
						dy = (int)releasePoint.getY() - refPoint.y;
						shot = new Shot(refPoint.x, refPoint.y, dx, dy, 0, tapTime);
					}
					else
						{
							System.err.println("No Release Point Found");
							return state;
						}
				}

				// check whether the slingshot is changed. the change of the slingshot indicates a change in the scale.
				{
					ActionRobot.fullyZoomOut();
					screenshot = ActionRobot.doScreenShot();
					vision = new Vision(screenshot);
					Rectangle _sling = vision.findSlingshotMBR();
					if(_sling != null)
					{
						double scale_diff = Math.pow((sling.width - _sling.width),2) +  Math.pow((sling.height - _sling.height),2);
						if(scale_diff < 25)
						{
							if(dx < 0)
							{
								aRobot.cshoot(shot);
								state = aRobot.getState();
								if ( state == GameState.PLAYING )
								{
									screenshot = ActionRobot.doScreenShot();
									vision = new Vision(screenshot);
									List<Point> traj = vision.findTrajPoints();
									tp.adjustTrajectory(traj, sling, releasePoint);
									previousShotNumber = shotNumber;
									shotNumber++;
								}
							}
						}
						else
							System.out.println("Scale is changed, can not execute the shot, will re-segement the image");
					}
					else
						System.out.println("no sling detected, can not execute the shot, will re-segement the image");
				}

			}

		}
		return state;
	}

	public static void main(String args[]) {

		NaiveAgent na = new NaiveAgent();
		if (args.length > 0)
			na.currentLevel = Integer.parseInt(args[0]);
		na.run();

	}
}
