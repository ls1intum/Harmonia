import StartAnalysis from '@/components/StartAnalysis';
import { useNavigate } from 'react-router-dom';

/** Landing page that renders the login/start-analysis form and navigates to the Teams page on success. */
export default function Home() {
  const navigate = useNavigate();

  const handleStartAnalysis = (course: string, exercise: string, username: string, password: string, pairProgrammingEnabled: boolean) => {
    navigate('/teams', { state: { course, exercise, username, password, pairProgrammingEnabled } });
  };

  return <StartAnalysis onStart={handleStartAnalysis} />;
}
