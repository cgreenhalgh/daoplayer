package org.opensharingtoolkit.daoplayer.audio;

import org.ejml.data.DenseMatrix64F;
import org.ejml.factory.LinearSolverFactory;
import org.ejml.interfaces.linsol.LinearSolver;

import android.util.Log;
import static org.ejml.ops.CommonOps.*;

/** (roughly?!) a kalman filter for GPS updates for user position.
 * initially trying to model pedestrian activity.
 * http://en.wikipedia.org/wiki/Kalman_filter
 * 
 * Trying EJML https://code.google.com/p/efficient-java-matrix-library/wiki/KalmanFilterExamples
 */
public class KalmanFilter {
	/** 4d State vector, x, will be (x(m), y(y), vx(m/s), vy(m/s). */
	private static final int STATE_N = 4;
	static final int STATE_X = 0;
	static final int STATE_Y = 1;
	static final int STATE_VX = 2;
	static final int STATE_VY = 3;
	/** 2D observation, x, y */
	private static final int OBSERVATION_N = 2;
	private static final int OBSERVATION_X = 0;
	private static final int OBSERVATION_Y = 1;
		/** metres; roughtly "no idea" where on earth */
	private static final int INITIAL_POSITION_UNCERTAINTY = 30000;
	/** m/s; roughly any plausible pedestrian speed */
	private static final int INITIAL_SPEED_UNCERTAINTY = 10;
	/** delta T; 1 second for now (like GPS) */
	private static final double DELTA_T = 1;
	/** SD of process noise acceleration, i.e. "reasonable" acceleration in m/s*s.
	 * At the start of the 100m Bolt manages 10 :-) */
	private static final double ACCELERATION_UNCERTAINTY = 2;

    // kinematics description
    private DenseMatrix64F F;
    private DenseMatrix64F Q;
    private DenseMatrix64F H;

    // system state estimate
    private DenseMatrix64F x;
    private DenseMatrix64F P;

    // these are predeclared for efficiency reasons
    private DenseMatrix64F a,b;
    private DenseMatrix64F y,S,S_inv,c,d;
    private DenseMatrix64F K;

    private LinearSolver<DenseMatrix64F> solver;
	
	public KalmanFilter() {
		// initialiase
		// state transition model, F 
		double transition [][] = new double[STATE_N][STATE_N];
		for (int i=0; i<STATE_N; i++)
			transition[i][i] = 1;
		// x = x+vx*dt, y=y+vy*dt;
		transition[STATE_X][STATE_VX] = DELTA_T;
		transition[STATE_Y][STATE_VY] = DELTA_T;
		DenseMatrix64F F = new DenseMatrix64F(transition);
		
		// no control for now (B, u)
		// process noise covariance matrix, Q 
		double processcovariance [][] = new double[STATE_N][STATE_N];
		// process noise covariance - random acceleration in X & Y.
		// constant over dt => dx of 0.5*a*dt*dt, dv of a*dt
		double dstate [] = new double[4];
		dstate[STATE_X] = dstate[STATE_Y] = 0.5*ACCELERATION_UNCERTAINTY/Math.sqrt(2)*DELTA_T*DELTA_T;
		dstate[STATE_VX] = dstate[STATE_VY] = ACCELERATION_UNCERTAINTY/Math.sqrt(2)*DELTA_T;
		// covariance = dstate * dstate-1
		// dimensions are independent
		for (int i=0; i<STATE_N; i+=2)
			for (int j=0; j<STATE_N; j+=2) {
				processcovariance[i][j] = dstate[i]*dstate[j];
				processcovariance[i+1][j+1] = dstate[i+1]*dstate[j+1];
			}
		DenseMatrix64F Q = new DenseMatrix64F(processcovariance);

		// observation model, H 
		double observationmodel [][] = new double[OBSERVATION_N][STATE_N];
		// observation model - x and y
		observationmodel[OBSERVATION_X][STATE_X] = 1;
		observationmodel[OBSERVATION_Y][STATE_Y] = 1;
		DenseMatrix64F H = new DenseMatrix64F(observationmodel);
	
		configure(F, Q, H);
		// observation noise covariance is provided with observation
		
		//  most recent state estimate, x
		double state [][] = new double[STATE_N][1];
		// state 0 is OK
		DenseMatrix64F x = new DenseMatrix64F(state);
		// state estimate covariance matrix, P 
		double statecovariance [][] = new double[STATE_N][STATE_N];
		statecovariance[STATE_X][STATE_X] = statecovariance[STATE_Y][STATE_Y] = INITIAL_POSITION_UNCERTAINTY*INITIAL_POSITION_UNCERTAINTY/2;
		statecovariance[STATE_VX][STATE_VX] = statecovariance[STATE_VY][STATE_VY] = INITIAL_SPEED_UNCERTAINTY*INITIAL_SPEED_UNCERTAINTY/2;
		DenseMatrix64F P = new DenseMatrix64F(statecovariance);
		
		setState(x, P);
		
	}
	
	public void update(double observationx, double observationy, double observationaccuracy) {
		// observation, z
		double observation[][] = new double[OBSERVATION_N][1];
		observation[OBSERVATION_X][0] = observationx;
		observation[OBSERVATION_Y][0] = observationy;
		// observation noise covariance matrix, R 
		double observationcovariance [][] = new double[OBSERVATION_N][OBSERVATION_N];
		observationcovariance[OBSERVATION_X][OBSERVATION_X] =
				observationcovariance[OBSERVATION_Y][OBSERVATION_Y] =
				observationaccuracy*observationaccuracy/2;
		
		DenseMatrix64F z = new DenseMatrix64F(observation);
		DenseMatrix64F R = new DenseMatrix64F(observationcovariance);
		update(z, R);
	}

	// implementation from https://code.google.com/p/efficient-java-matrix-library/wiki/KalmanFilterExamples
    public void configure(DenseMatrix64F F, DenseMatrix64F Q, DenseMatrix64F H) {
        this.F = F;
        this.Q = Q;
        this.H = H;

        int dimenX = F.numCols;
        int dimenZ = H.numRows;

        a = new DenseMatrix64F(dimenX,1);
        b = new DenseMatrix64F(dimenX,dimenX);
        y = new DenseMatrix64F(dimenZ,1);
        S = new DenseMatrix64F(dimenZ,dimenZ);
        S_inv = new DenseMatrix64F(dimenZ,dimenZ);
        c = new DenseMatrix64F(dimenZ,dimenX);
        d = new DenseMatrix64F(dimenX,dimenZ);
        K = new DenseMatrix64F(dimenX,dimenZ);

        x = new DenseMatrix64F(dimenX,1);
        P = new DenseMatrix64F(dimenX,dimenX);

        // covariance matrices are symmetric positive semi-definite
        solver = LinearSolverFactory.symmPosDef(dimenX);
    }

    public void setState(DenseMatrix64F x, DenseMatrix64F P) {
        this.x.set(x);
        this.P.set(P);
    }

    public void predict() {

        // x = F x
        mult(F,x,a);
        x.set(a);

        // P = F P F' + Q
        mult(F,P,b);
        multTransB(b,F, P);
        addEquals(P,Q);
    }

    public void update(DenseMatrix64F z, DenseMatrix64F R) {
        // y = z - H x
        mult(H,x,y);
        subtract(z, y, y);

        // S = H P H' + R
        mult(H,P,c);
        multTransB(c,H,S);
        addEquals(S,R);

        // K = PH'S^(-1)
        if( !solver.setA(S) ) throw new RuntimeException("Invert failed");
        solver.invert(S_inv);
        multTransA(H,S_inv,d);
        mult(P,d,K);

        // x = x + Ky
        mult(K,y,a);
        //Log.d("update", "z="+z.toString()+", x="+x+", y="+y.toString()+", K="+K);
        addEquals(x,a);

        // P = (I-kH)P = P - (KH)P = P-K(HP)
        mult(H,P,c);
        mult(K,c,b);
        subtractEquals(P, b);
    }

    public DenseMatrix64F getState() {
        return x;
    }

    public DenseMatrix64F getCovariance() {
        return P;
    }
}
