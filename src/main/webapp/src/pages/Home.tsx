import StartAnalysis from "@/components/StartAnalysis";
import { useNavigate } from "react-router-dom";

export default function Home() {
    const navigate = useNavigate();

    const handleStartAnalysis = (course: string, exercise: string) => {
        navigate('/teams', { state: { course, exercise } });
    };

    return <StartAnalysis onStart={handleStartAnalysis} />;
}